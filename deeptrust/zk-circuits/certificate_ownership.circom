pragma circom 2.1.6;

include "circomlib/circuits/poseidon.circom";
include "circomlib/circuits/comparators.circom";

/*
 * CertificateOwnership circuit
 * -----------------------------------------------------------------------
 * Proves: "I possess a file whose SHA-256 hash, when committed via
 * Poseidon, equals the on-chain/DB-registered commitment for certificate X
 * — without revealing the underlying SHA-256 hash itself in the proof."
 *
 * Why Poseidon instead of hashing SHA-256 directly inside the circuit:
 * SHA-256 has ~27,000 constraints per call in an R1CS circuit, which is
 * expensive but still tractable for a single hash — however Poseidon is
 * purpose-built for ZK circuits (~300 constraints) and is what the
 * commitment layer uses. The flow is:
 *   1. Off-chain (Java): sha256Hash = SHA256(file bytes)              [Module 2, unchanged]
 *   2. Off-chain (Java): commitment = Poseidon(sha256Hash, salt)      [new, at certificate issuance]
 *   3. On-chain / DB: only `commitment` is stored, never the raw hash
 *   4. Verifier time: prover supplies {sha256Hash, salt} as private
 *      inputs and `commitment` as public input; circuit checks
 *      Poseidon(sha256Hash, salt) == commitment and proves it succeeded
 *      WITHOUT the verifier ever learning sha256Hash or salt.
 *
 * This is the genuine "zero-knowledge" claim behind Module 6 — the
 * plain hash-comparison endpoint (VerificationService) is a fast,
 * practical integrity check; this circuit is the cryptographic proof
 * used when a verifier must be convinced without being shown the hash.
 */
template CertificateOwnership() {
    // Private inputs — never revealed to the verifier
    signal input sha256HashChunks[2]; // SHA-256 (256 bits) split into two 128-bit field-safe chunks
    signal input salt;

    // Public input — the on-chain/DB commitment being proven against
    signal input commitment;

    // Public output — 1 if the proof is valid, circuit itself only
    // "compiles" a valid proof when the constraint holds, so this signal
    // is mostly for explicitness / off-chain readability.
    signal output valid;

    component poseidon = Poseidon(3);
    poseidon.inputs[0] <== sha256HashChunks[0];
    poseidon.inputs[1] <== sha256HashChunks[1];
    poseidon.inputs[2] <== salt;

    component eq = IsEqual();
    eq.in[0] <== poseidon.out;
    eq.in[1] <== commitment;

    valid <== eq.out;

    // Hard constraint: the proof simply does not verify unless this holds.
    eq.out === 1;
}

component main { public [commitment] } = CertificateOwnership();
