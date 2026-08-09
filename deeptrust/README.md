# DeepTrust — Multimodal Deepfake Authentication and Blockchain Verification Framework

Final-year engineering project. Combines an operational MySQL database, an
isolated Python AI microservice, a Solidity smart contract anchored on a
local Ethereum testnet (Ganache), a Spring Boot backend, a React frontend,
and a zk-SNARK circuit for cryptographic zero-knowledge verification.

## Architecture Boundaries

| Layer | Stores | Never stores |
|---|---|---|
| **MySQL** (operational DB) | users, media metadata, SHA-256 + pHash, AI analysis, audit logs, certificates | — |
| **Blockchain** (immutable ledger) | certificate ID, SHA-256 hash, result code, timestamp | PII, filenames, media bytes |
| **AI microservice** (Python, isolated) | nothing — stateless, returns JSON only | any persisted data |

## Modules

1. **Authentication & Audit Logging** — JWT auth, RBAC (`ROOT`, `ANALYST`,
   `INVESTIGATOR`, `OPERATOR`, `VIEWER`), and a **hash-chained, tamper-evident
   audit log** (`AuditChainService` + `AuditChainVerificationService`) — any
   row edited directly in MySQL breaks the chain from that point forward,
   detectable via `GET /api/admin/audit/verify-chain`.
2. **Dual-Hash Media Upload** — SHA-256 (exact byte match) + JImageHash
   perceptual hash (compression/resize-resistant) computed on every upload.
3. **AI Inference Integration** — calls the isolated Python microservice,
   which performs **multimodal fusion** (visual + audio + lip-sync
   consistency scoring) for video, not just a single-modality classifier.
4. **Certificate Generation** — links Media + Analysis + User into a
   `DT-YYYY-NNNNNN` certificate.
5. **Blockchain Anchoring** — `BlockchainAnchorService` (Web3j) anchors
   certificates to `DeepTrustRegistry.sol` on Ganache.
6. **Zero-Knowledge Verification** — two layers:
   - Practical hash-comparison verification (`VerificationService`) for
     everyday integrity checks.
   - A genuine Groth16 zk-SNARK circuit (`zk-circuits/certificate_ownership.circom`)
     that proves possession of a matching file **without revealing its hash**
     to the verifier, via a Poseidon commitment scheme.
7. **Certificate Revocation** — modeled as an **appended event**
   (`CertificateStatusEvent` + `CertificateStatusChanged` on-chain), never a
   mutation or delete, preserving full history.
8. **Public QR Verification** — every certificate gets a QR code
   (`GET /api/public/qr/{code}`) linking to a no-login page
   (`GET /api/public/verify/{code}`) that independently cross-checks MySQL
   against the blockchain before showing a status.

## Repo layout

```
deeptrust/
├── backend/          Spring Boot 3 / Java 21 API
├── blockchain/        DeepTrustRegistry.sol + Hardhat project
├── ai-service/         Isolated Python/FastAPI inference microservice
├── zk-circuits/         circom circuit + snarkjs build pipeline
├── frontend/           React + TypeScript + Tailwind (public verify page)
└── docs/
```

## Running locally

### 1. Blockchain (Ganache + contract deploy)
```bash
npm install -g ganache
ganache --port 8545 &

cd blockchain
npm install
npm run compile
export GANACHE_PRIVATE_KEY=<one of ganache's printed private keys>
npm run deploy:ganache
# copy the printed contract address
```

### 2. AI microservice
```bash
cd ai-service
python -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 3. Backend
```bash
cd backend
# Required env vars — see application.yml for the full list
export JWT_SECRET=$(openssl rand -base64 64)
export DB_USERNAME=deeptrust_app DB_PASSWORD=<your-mysql-password>
export BLOCKCHAIN_WALLET_PRIVATE_KEY=<same ganache key used above>
export DEEPTRUST_CONTRACT_ADDRESS=<address from step 1>
export DEEPTRUST_ROOT_USERNAME=ranjitha DEEPTRUST_ROOT_EMAIL=ranjitha@deeptrust.dev
export DEEPTRUST_ROOT_INITIAL_PASSWORD=<set once, forced rotation on first login>

mvn spring-boot:run
```

### 4. Frontend
```bash
cd frontend
npm install
npm run dev
# open http://localhost:5173/verify/DT-2026-000001
```

## Security notes worth knowing for the viva defense

- **ROOT is never hardcoded.** `RootUserBootstrap` creates it once, only if
  no ROOT exists, entirely from environment variables, with forced password
  rotation on first login.
- **Audit log integrity is independently verifiable** — the hash chain can
  be recomputed by anyone with read access to the table, without trusting
  a DBA's word that nothing was altered.
- **Revocation never deletes history** — both in MySQL
  (`certificate_status_events`) and on-chain (`CertificateStatusChanged`),
  matching the immutable-ledger philosophy end-to-end.
- **Public verification cross-checks two independent systems** (MySQL +
  blockchain) before declaring a certificate valid, so a compromised DB row
  alone cannot forge a false positive.
- **The ZK circuit is a real cryptographic proof**, not a rebrand of hash
  comparison — see `zk-circuits/README.md` for exactly what it proves and why.
