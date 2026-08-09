# DeepTrust ZK Circuit — Certificate Ownership Proof

## What this proves

That the prover possesses a file whose SHA-256 hash matches the commitment
registered for a given certificate, **without revealing the SHA-256 hash
itself** to the verifier. This is the cryptographic zero-knowledge layer
behind Module 6; the plain hash-comparison endpoint in
`VerificationService` is a separate, non-ZK integrity check used for the
everyday "does this file match?" flow.

## Why a commitment layer (Poseidon) instead of proving SHA-256 directly

SHA-256 works fine as the *integrity* hash (Module 2), but proving
"I know a preimage of this SHA-256 hash" inside a SNARK circuit is
expensive (~27k constraints). Poseidon is a hash function designed to be
cheap inside arithmetic circuits (~300 constraints). So DeepTrust uses a
two-layer scheme:

1. **SHA-256** — computed in Java (`FileHashingService`), stored in MySQL,
   anchored on-chain. This is the fast, everyday integrity hash.
2. **Poseidon commitment** — computed once at certificate issuance time as
   `commitment = Poseidon(sha256Hash, salt)`. Only this commitment (plus a
   the salt kept private with the certificate owner) is ever exposed
   publicly. The ZK circuit proves knowledge of a `(sha256Hash, salt)` pair
   that hashes to the public `commitment`, without revealing either value.

## Build pipeline (Groth16 via snarkjs)

```bash
# 1. Install circom + circomlib
npm install -g circom
npm install circomlib

# 2. Compile the circuit
circom certificate_ownership.circom --r1cs --wasm --sym -o build/

# 3. Powers of Tau trusted setup (Groth16 needs this — use a public,
#    already-contributed ptau file for anything beyond a toy demo;
#    generating your own single-party ptau is fine for a viva but is
#    NOT a trust assumption you'd want in production)
snarkjs powersoftau new bn128 14 build/pot14_0000.ptau -v
snarkjs powersoftau contribute build/pot14_0000.ptau build/pot14_final.ptau --name="deeptrust" -v
snarkjs powersoftau prepare phase2 build/pot14_final.ptau build/pot14_prepared.ptau -v

# 4. Circuit-specific setup
snarkjs groth16 setup build/certificate_ownership.r1cs build/pot14_prepared.ptau build/circuit_0000.zkey
snarkjs zkey contribute build/circuit_0000.zkey build/circuit_final.zkey --name="deeptrust-1" -v
snarkjs zkey export verificationkey build/circuit_final.zkey build/verification_key.json

# 5. Export a Solidity verifier (optional — lets the chain itself verify proofs)
snarkjs zkey export solidityverifier build/circuit_final.zkey ../blockchain/contracts/Groth16Verifier.sol
```

## Runtime flow

- `ZkProofService` (Java) shells out to `snarkjs` via `ProcessBuilder` to
  generate a witness + proof for a given `(sha256Hash, salt, commitment)`
  triple, and to verify proofs against `verification_key.json`.
- For a production system, replace the `ProcessBuilder` shell-out with a
  proper snarkjs Node microservice behind a REST API (mirrors the
  isolation pattern already used for the Python AI service) — shelling out
  from the JVM is fine for a viva demo but not how you'd want to run this
  at scale or under untrusted multi-tenant load.
