<div align="center">

# 🛡️ DeepTrust

### Multimodal Deepfake Authentication & Blockchain Verification Framework

*A forensic-grade system for detecting manipulated media and issuing tamper-proof, blockchain-anchored authenticity certificates.*

[![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3-brightgreen?logo=springboot)](https://spring.io/projects/spring-boot)
[![Solidity](https://img.shields.io/badge/Solidity-0.8.24-363636?logo=solidity)](https://soliditylang.org/)
[![React](https://img.shields.io/badge/React-18-61DAFB?logo=react)](https://react.dev/)
[![Python](https://img.shields.io/badge/Python-3.11-3776AB?logo=python&logoColor=white)](https://python.org/)
[![MySQL](https://img.shields.io/badge/MySQL-8.x-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)

[Overview](#-overview) • [Architecture](#-architecture) • [Features](#-features) • [Tech Stack](#-tech-stack) • [Getting Started](#-getting-started) • [Security](#-security-highlights) • [Roadmap](#-roadmap)

</div>

---

## 📖 Overview

**DeepTrust** is a full-stack system that lets anyone verify whether a piece of media (image, video, or audio) is authentic or AI-manipulated, and issues a **cryptographically anchored certificate of authenticity** for content that passes verification.

Every certificate is:
- **Dual-hashed** — SHA-256 for exact byte integrity, perceptual hash (pHash) for compression/resize-resistant matching
- **AI-verified** — multimodal deepfake detection (visual + audio + lip-sync fusion for video)
- **Blockchain-anchored** — immutable, publicly auditable proof that can't be silently altered
- **Revocable without deletion** — a wrongly-issued certificate can be revoked, but the full history is preserved forever
- **Publicly verifiable** — scan a QR code, no login required, get a live cross-check between the database and the chain

Built as a final-year engineering capstone project.

---

## 🏗️ Architecture

DeepTrust deliberately separates concerns across independent trust boundaries:

```
┌─────────────────┐      ┌──────────────────────┐      ┌───────────────────┐
│   React Frontend │◄────►│   Spring Boot Backend │◄────►│   MySQL Database   │
│  (public verify,  │      │  (auth, orchestration, │      │  (users, media,     │
│   dashboards)     │      │   RBAC, audit chain)  │      │   hashes, audit log,│
└─────────────────┘      └──────────┬───────────┘      │   certificates)     │
                                     │                    └───────────────────┘
                     ┌───────────────┼────────────────┐
                     ▼               ▼                ▼
          ┌─────────────────┐ ┌──────────────┐ ┌─────────────────┐
          │  Python AI       │ │  Ethereum     │ │  zk-SNARK Prover  │
          │  Microservice    │ │  (Ganache +   │ │  (circom +        │
          │  (isolated,      │ │  DeepTrust-   │ │  snarkjs)          │
          │  stateless)      │ │  Registry.sol)│ │                    │
          └─────────────────┘ └──────────────┘ └─────────────────┘
```

**Strict data boundaries:**

| Layer | Stores | Never stores |
|---|---|---|
| **MySQL** (operational DB) | Users, media metadata, SHA-256 + pHash, AI analysis, audit logs, certificates | — |
| **Blockchain** (immutable ledger) | Certificate ID, SHA-256 hash, result code, timestamp | PII, filenames, media bytes |
| **AI microservice** (Python) | Nothing — stateless, returns JSON only | Any persisted data |

---

## ✨ Features

### Module 1 — Authentication & Tamper-Evident Audit Logging
- JWT-based auth with strict RBAC: `ROOT`, `ANALYST`, `INVESTIGATOR`, `OPERATOR`, `VIEWER`
- `ROOT` is founder-reserved, provisioned only via secure environment-variable bootstrap — never hardcoded
- **Hash-chained audit logs**: every entry embeds the hash of the previous entry, so any row edited directly in MySQL breaks the chain from that point forward — independently verifiable via `GET /api/admin/audit/verify-chain`

### Module 2 — Dual-Hash Media Upload
- SHA-256 for exact byte-level integrity
- Perceptual hash (JImageHash) that survives re-compression, resizing, and format conversion
- Content-addressed storage with automatic exact-duplicate detection

### Module 3 — Multimodal AI Inference
- Calls an isolated Python/FastAPI microservice over REST — no direct DB or blockchain access from the AI layer
- **True multimodal fusion** for video: visual manipulation score + audio spoofing score + lip-sync consistency score, weighted into a single verdict — not just three separate detectors
- Returns `AUTHENTIC` / `MANIPULATED` / `INCONCLUSIVE` with confidence and heatmap regions

### Module 4 — Certificate Generation
- Links Media + Analysis + User into a unique certificate (`DT-2026-000001` format)

### Module 5 — Blockchain Anchoring
- Web3j bridge to `DeepTrustRegistry.sol`, deployed on a local Ganache testnet
- Only the certificate ID, SHA-256 hash, result code, and timestamp ever cross onto the chain

### Module 6 — Zero-Knowledge Verification
- **Practical layer**: fast hash-comparison verification against MySQL + blockchain for everyday integrity checks
- **Cryptographic layer**: a real Groth16 zk-SNARK circuit (`certificate_ownership.circom`) using a Poseidon commitment scheme — proves possession of a matching file **without revealing its hash** to the verifier

### Feature — Certificate Revocation (Immutable-Ledger Safe)
- Revocation is modeled as an **appended event**, never a mutation or delete
- Both MySQL (`certificate_status_events`) and the smart contract (`CertificateStatusChanged`) preserve full history — a revoked certificate's original record is never erased

### Feature — Public QR Verification
- Every certificate gets a scannable QR code
- The linked page requires **no login** and independently cross-checks the database against the blockchain before showing a trust status — so a compromised DB row alone can't forge a "verified" result

---

## 🧰 Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security (JWT), Spring Data JPA, Lombok |
| Database | MySQL 8, Hibernate |
| Blockchain | Solidity 0.8.24, Hardhat, Ganache, Web3j |
| AI Microservice | Python 3.11, FastAPI, Pillow/ImageHash |
| Zero-Knowledge | circom, snarkjs, Groth16 |
| Frontend | React 18, TypeScript, Tailwind CSS, Vite |
| Perceptual Hashing | JImageHash (Java), ImageHash (Python) |
| QR Codes | ZXing |

---

## 📁 Project Structure

```
deeptrust/
├── backend/            # Spring Boot 3 / Java 21 API
│   └── src/main/java/com/deeptrust/
│       ├── auth/          # Login, refresh, JWT issuance
│       ├── security/      # JWT filter, RBAC config, principal
│       ├── user/          # User entity, roles, repository
│       ├── audit/         # Hash-chained tamper-evident logs
│       ├── media/         # Upload, SHA-256 + pHash hashing
│       ├── analysis/      # AI microservice client
│       ├── certificate/   # Issuance + append-only revocation
│       ├── blockchain/    # Web3j bridge to DeepTrustRegistry.sol
│       ├── verify/        # Verification + public QR endpoints
│       ├── zk/             # Zero-knowledge proof service
│       └── bootstrap/     # Secure ROOT account provisioning
├── blockchain/          # DeepTrustRegistry.sol + Hardhat project
├── ai-service/           # Isolated Python/FastAPI inference microservice
├── zk-circuits/           # circom circuit + snarkjs build pipeline
├── frontend/             # React + TypeScript + Tailwind
└── docs/
```

---

## 🚀 Getting Started

### Prerequisites

- Java 21+ & Maven
- MySQL 8.x
- Node.js 18+
- Python 3.11+
- Ganache (`npm install -g ganache`)

### 1. Database

```sql
CREATE DATABASE deeptrust CHARACTER SET utf8mb4;
CREATE USER 'deeptrust_app'@'localhost' IDENTIFIED BY 'your-strong-password';
GRANT ALL PRIVILEGES ON deeptrust.* TO 'deeptrust_app'@'localhost';
FLUSH PRIVILEGES;
```

### 2. Blockchain

```bash
ganache --port 8545 &

cd blockchain
npm install && npm run compile
export GANACHE_PRIVATE_KEY=<a key printed by ganache>
npm run deploy:ganache   # copy the deployed contract address
```

### 3. AI Microservice

```bash
cd ai-service
python3 -m venv venv && source venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### 4. Backend

```bash
cd backend
export JWT_SECRET=$(openssl rand -base64 64)
export DB_USERNAME=deeptrust_app DB_PASSWORD=your-strong-password
export BLOCKCHAIN_WALLET_PRIVATE_KEY=<same ganache key as above>
export DEEPTRUST_CONTRACT_ADDRESS=<address from step 2>
export DEEPTRUST_ROOT_USERNAME=ranjitha
export DEEPTRUST_ROOT_EMAIL=ranjitha@deeptrust.dev
export DEEPTRUST_ROOT_INITIAL_PASSWORD=<set once, forced rotation on first login>

mvn spring-boot:run
```

### 5. Frontend

```bash
cd frontend
npm install
npm run dev
# open http://localhost:5173
```

Full environment variable reference: [`.env.example`](.env.example)

---

## 🔐 Security Highlights

- **No hardcoded credentials, anywhere** — the founder `ROOT` account is bootstrapped once from environment variables with forced password rotation on first login
- **Tamper-evident audit trail** — the hash chain can be independently recomputed and verified by anyone with read access to the table, without needing to trust a database administrator's word
- **Revocation never destroys history** — matches the immutable-ledger philosophy on both the MySQL and blockchain sides
- **Two-system cross-verification** — public certificate checks require agreement between MySQL and the blockchain before returning a "verified" status
- **Client-supplied MIME types are never trusted alone** — flagged for magic-byte validation hardening
- **JWT access/refresh token rotation** with short-lived access tokens and refresh-token rotation on use

---

## 🗺️ Roadmap

- [ ] Wire the Poseidon commitment helper for full ZK proof generation
- [ ] Replace placeholder AI inference with trained deepfake detection models
- [ ] Merkle-batched blockchain anchoring for gas efficiency at scale
- [ ] Human-in-the-loop review queue for `INCONCLUSIVE` verdicts
- [ ] Flyway/Liquibase migrations in place of Hibernate `ddl-auto`
- [ ] Chrome extension for one-click social media verification

---

## 📄 License

This project is licensed under the MIT License — see [LICENSE](LICENSE) for details.

---

<div align="center">

Built as a final-year engineering capstone project.

</div>
