package com.deeptrust.zk;

public class ZkDtos {

    public record CommitmentResponse(String commitmentHex, String saltHex) {}

    public record ProofRequest(String sha256HashHex, String saltHex, String commitmentHex) {}

    public record ProofResponse(String proofJson, String publicSignalsJson) {}

    public record VerifyProofRequest(String proofJson, String publicSignalsJson) {}

    public record VerifyProofResponse(boolean valid) {}
}
