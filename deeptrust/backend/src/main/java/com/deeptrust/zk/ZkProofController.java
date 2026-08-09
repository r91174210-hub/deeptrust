package com.deeptrust.zk;

import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Exposes the zero-knowledge proof workflow described in
 * zk-circuits/certificate_ownership.circom:
 *   1. POST /commitment   -> issuer computes Poseidon(sha256Hash, salt) at
 *                            certificate issuance; salt returned once to the
 *                            owner and never stored in plaintext thereafter.
 *   2. POST /proof         -> owner (holding sha256Hash + salt) generates a
 *                            Groth16 proof against the public commitment.
 *   3. POST /proof/verify  -> ANYONE can verify the proof using only the
 *                            public commitment + proof — no hash or salt
 *                            ever needs to be transmitted at verify time.
 */
@RestController
@RequestMapping("/api/zk")
@RequiredArgsConstructor
public class ZkProofController {

    private final ZkProofService zkProofService;
    private final AuditLogService auditLogService;

    @PostMapping("/commitment")
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ROOT')")
    public ZkDtos.CommitmentResponse computeCommitment(@RequestParam String sha256HashHex,
                                                         @AuthenticationPrincipal CurrentUser user) {
        ZkProofService.Commitment commitment = zkProofService.computeCommitment(sha256HashHex);
        auditLogService.logAction(user.getId(), "ZK_COMMITMENT_CREATED", null, null,
                "commitment=" + commitment.commitmentHex());
        return new ZkDtos.CommitmentResponse(commitment.commitmentHex(), commitment.saltHex());
    }

    @PostMapping("/proof")
    @PreAuthorize("isAuthenticated()")
    public ZkDtos.ProofResponse generateProof(@RequestBody ZkDtos.ProofRequest request) {
        ZkProofService.ZkProof proof = zkProofService.generateProof(
                request.sha256HashHex(), request.saltHex(), request.commitmentHex());
        return new ZkDtos.ProofResponse(proof.proofJson(), proof.publicSignalsJson());
    }

    /** Public: verifying a proof requires no login and reveals nothing private. */
    @PostMapping("/proof/verify")
    public ZkDtos.VerifyProofResponse verifyProof(@RequestBody ZkDtos.VerifyProofRequest request) {
        boolean valid = zkProofService.verifyProof(request.proofJson(), request.publicSignalsJson());
        return new ZkDtos.VerifyProofResponse(valid);
    }
}
