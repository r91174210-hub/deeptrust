package com.deeptrust.zk;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Bridges to the compiled circom circuit + snarkjs toolchain to generate and
 * verify real Groth16 zk-SNARK proofs of certificate ownership, per
 * zk-circuits/certificate_ownership.circom.
 *
 * Design note: this shells out to the `snarkjs` CLI via ProcessBuilder,
 * which is adequate for a single-instance viva/demo deployment. For
 * production, this should be a dedicated, isolated microservice (mirroring
 * the AI inference service's isolation pattern) invoked over REST, so the
 * JVM process never directly executes external binaries.
 */
@Slf4j
@Service
public class ZkProofService {

    @Value("${deeptrust.zk.circuit-build-dir:./zk-circuits/build}")
    private String circuitBuildDir;

    @Value("${deeptrust.zk.snarkjs-path:snarkjs}")
    private String snarkjsExecutable;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom secureRandom = new SecureRandom();

    public record Commitment(String commitmentHex, String saltHex) {}
    public record ZkProof(String proofJson, String publicSignalsJson) {}

    /**
     * Computes the Poseidon commitment for a SHA-256 hash at certificate
     * issuance time. The salt must be kept by whoever will later prove
     * ownership (typically returned to the certificate owner once, never
     * stored in plaintext server-side beyond that).
     */
    public Commitment computeCommitment(String sha256HashHex) {
        byte[] salt = new byte[16];
        secureRandom.nextBytes(salt);
        String saltHex = bytesToHex(salt);

        // The actual Poseidon(sha256Hash, salt) computation happens inside
        // the snarkjs/circom toolchain via the witness calculator to stay
        // consistent with the circuit's field arithmetic. This method
        // shells out to a small helper script that loads the compiled
        // circuit's wasm and computes Poseidon using the same library.
        String commitment = runPoseidonHelper(sha256HashHex, saltHex);
        return new Commitment(commitment, saltHex);
    }

    /** Generates a Groth16 proof that the prover knows (sha256Hash, salt) matching the public commitment. */
    public ZkProof generateProof(String sha256HashHex, String saltHex, String commitmentHex) {
        try {
            Path workDir = Files.createTempDirectory("deeptrust-zk-" + UUID.randomUUID());
            Path inputFile = workDir.resolve("input.json");

            Map<String, Object> input = Map.of(
                    "sha256HashChunks", splitHashIntoChunks(sha256HashHex),
                    "salt", hexToFieldDecimal(saltHex),
                    "commitment", commitmentHex
            );
            Files.writeString(inputFile, objectMapper.writeValueAsString(input), StandardCharsets.UTF_8);

            runCommand(workDir,
                    "node", circuitBuildDir + "/certificate_ownership_js/generate_witness.js",
                    circuitBuildDir + "/certificate_ownership_js/certificate_ownership.wasm",
                    inputFile.toString(),
                    workDir.resolve("witness.wtns").toString());

            runCommand(workDir,
                    snarkjsExecutable, "groth16", "prove",
                    circuitBuildDir + "/circuit_final.zkey",
                    workDir.resolve("witness.wtns").toString(),
                    workDir.resolve("proof.json").toString(),
                    workDir.resolve("public.json").toString());

            String proofJson = Files.readString(workDir.resolve("proof.json"));
            String publicJson = Files.readString(workDir.resolve("public.json"));

            deleteRecursively(workDir.toFile());
            return new ZkProof(proofJson, publicJson);

        } catch (IOException | InterruptedException e) {
            log.error("ZK proof generation failed", e);
            throw new IllegalStateException("Failed to generate zero-knowledge proof: " + e.getMessage(), e);
        }
    }

    /** Verifies a Groth16 proof against the circuit's verification key — no private inputs ever touch this method. */
    public boolean verifyProof(String proofJson, String publicSignalsJson) {
        try {
            Path workDir = Files.createTempDirectory("deeptrust-zk-verify-" + UUID.randomUUID());
            Files.writeString(workDir.resolve("proof.json"), proofJson);
            Files.writeString(workDir.resolve("public.json"), publicSignalsJson);

            Process process = new ProcessBuilder(
                    snarkjsExecutable, "groth16", "verify",
                    circuitBuildDir + "/verification_key.json",
                    workDir.resolve("public.json").toString(),
                    workDir.resolve("proof.json").toString()
            ).redirectErrorStream(true).start();

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            deleteRecursively(workDir.toFile());

            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("ZK verification timed out");
            }
            // snarkjs prints "[INFO]  snarkJS: OK!" on success.
            return output.contains("OK!");

        } catch (IOException | InterruptedException e) {
            log.error("ZK proof verification failed", e);
            return false;
        }
    }

    private String runPoseidonHelper(String sha256HashHex, String saltHex) {
        // In a full build, this invokes a small Node helper (circomlibjs)
        // that computes Poseidon(sha256HashChunks, salt) identically to
        // the circuit's field arithmetic, guaranteeing the off-chain
        // commitment and in-circuit constraint agree exactly. Wiring that
        // helper script is a packaging detail left for zk-circuits/README.md;
        // this method is the single call site to swap in the real binary.
        throw new UnsupportedOperationException(
                "Wire this to the compiled Poseidon helper per zk-circuits/README.md before enabling ZK issuance.");
    }

    private long[] splitHashIntoChunks(String sha256HashHex) {
        // Splits a 256-bit hex hash into two 128-bit field-safe decimal chunks.
        String high = sha256HashHex.substring(0, 32);
        String low = sha256HashHex.substring(32, 64);
        return new long[]{Long.parseUnsignedLong(high, 16), Long.parseUnsignedLong(low, 16)};
    }

    private String hexToFieldDecimal(String hex) {
        return new java.math.BigInteger(hex, 16).toString();
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void runCommand(Path workDir, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).directory(workDir.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command) + "\n" + output);
        }
    }

    private void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursively(child);
        }
        file.delete();
    }
}
