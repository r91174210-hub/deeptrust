package com.deeptrust.verify.service;

import com.deeptrust.analysis.entity.Analysis;
import com.deeptrust.analysis.repository.AnalysisRepository;
import com.deeptrust.audit.service.AuditLogService;
import com.deeptrust.blockchain.BlockchainAnchorService;
import com.deeptrust.certificate.entity.Certificate;
import com.deeptrust.certificate.repository.CertificateRepository;
import com.deeptrust.media.entity.Media;
import com.deeptrust.media.repository.MediaRepository;
import com.deeptrust.media.service.FileHashingService;
import com.deeptrust.verify.dto.VerificationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/**
 * Module 6 (Zero-Knowledge-style verification) + Feature #7 (public QR
 * verification). A user uploads a file; we compute its SHA-256 and pHash
 * and compare against MySQL, then independently cross-check certificate
 * status against the blockchain so a compromised DB row alone can't forge
 * a false "ACTIVE" result.
 *
 * Note: this endpoint proves integrity/tamper-detection via hash comparison.
 * The stronger cryptographic zero-knowledge guarantee (proving possession of
 * a matching file without revealing its hash to the verifier at all) is
 * implemented separately in com.deeptrust.zk.ZkProofService.
 */
@Service
@RequiredArgsConstructor
public class VerificationService {

    // Perceptual hashes within this Hamming distance are considered the
    // "same visual content" despite re-compression/resizing. Tune based on
    // the hash bit-length (64-bit here) and empirical false-positive rate.
    private static final int PHASH_SIMILARITY_THRESHOLD = 10;

    private final FileHashingService fileHashingService;
    private final MediaRepository mediaRepository;
    private final AnalysisRepository analysisRepository;
    private final CertificateRepository certificateRepository;
    private final BlockchainAnchorService blockchainAnchorService;
    private final AuditLogService auditLogService;

    public VerificationResult verifyUploadedFile(MultipartFile file, Long verifyingUserId) {
        String sha256 = fileHashingService.sha256(file);
        String pHash = fileHashingService.perceptualHash(file);

        Optional<Media> exactMatch = mediaRepository.findBySha256Hash(sha256);

        if (exactMatch.isPresent()) {
            VerificationResult result = buildResult(exactMatch.get(), true, 0);
            auditLogService.logAction(verifyingUserId, "VERIFICATION_EXACT_MATCH", "Media",
                    exactMatch.get().getId(), "sha256=" + sha256);
            return result;
        }

        // No exact match — search for a perceptually similar registered
        // original (catches recompression, resizing, format conversion).
        if (pHash != null) {
            for (Media candidate : mediaRepository.findByPerceptualHash(pHash)) {
                int distance = 0; // identical pHash string => distance 0
                return finalizeSimilarMatch(candidate, distance, verifyingUserId, sha256);
            }
            // Broader scan across all media would be needed for a real
            // near-duplicate search at scale (e.g. via a pHash index/LSH);
            // for the viva-scope demo we compare against exact pHash matches
            // and note the extension point.
        }

        auditLogService.logAction(verifyingUserId, "VERIFICATION_NO_MATCH", null, null, "sha256=" + sha256);

        return new VerificationResult(
                false, false, -1, null, null, null, 0.0, null, false, null,
                "No matching media found in the registry. This file has not been certified by DeepTrust."
        );
    }

    /** Public, no-auth lookup by certificate code — powers the QR-code verification page. */
    public VerificationResult verifyByCertificateCode(String certificateCode) {
        Certificate certificate = certificateRepository.findByCertificateCode(certificateCode)
                .orElse(null);

        if (certificate == null) {
            return new VerificationResult(
                    false, false, -1, certificateCode, null, null, 0.0, null, false, null,
                    "No certificate found with this code."
            );
        }

        Media media = mediaRepository.findById(certificate.getMediaId()).orElse(null);
        Analysis analysis = analysisRepository.findById(certificate.getAnalysisId()).orElse(null);

        // Independent on-chain confirmation — this is the step that makes
        // the public page trustworthy even if MySQL were compromised.
        boolean chainConfirmed = blockchainAnchorService.isActiveOnChain(certificateCode)
                == (certificate.getStatus().name().equals("ACTIVE"));

        String message = certificate.getStatus().name().equals("REVOKED")
                ? "This certificate has been REVOKED. Do not treat this media as verified."
                : chainConfirmed
                    ? "Certificate is ACTIVE and confirmed on-chain."
                    : "Warning: database and blockchain records disagree on this certificate's status.";

        return new VerificationResult(
                true, true, 0,
                certificate.getCertificateCode(),
                certificate.getStatus().name(),
                analysis != null ? analysis.getAiResult().name() : null,
                analysis != null ? analysis.getConfidence() : 0.0,
                certificate.getBlockchainTxHash(),
                chainConfirmed,
                certificate.getCreatedAt(),
                message
        );
    }

    private VerificationResult finalizeSimilarMatch(Media media, int distance, Long verifyingUserId, String uploadedSha256) {
        VerificationResult result = buildResult(media, false, distance);
        auditLogService.logAction(verifyingUserId, "VERIFICATION_PERCEPTUAL_MATCH", "Media",
                media.getId(), "uploadedSha256=" + uploadedSha256 + " distance=" + distance);
        return result;
    }

    private VerificationResult buildResult(Media media, boolean exact, int hammingDistance) {
        Analysis analysis = analysisRepository.findByMediaId(media.getId()).orElse(null);
        Certificate certificate = certificateRepository.findByMediaId(media.getId()).orElse(null);

        boolean chainConfirmed = certificate != null
                && blockchainAnchorService.isActiveOnChain(certificate.getCertificateCode())
                    == certificate.getStatus().name().equals("ACTIVE");

        String message;
        if (certificate == null) {
            message = "Media matches a registered file, but no certificate has been issued yet.";
        } else if (certificate.getStatus().name().equals("REVOKED")) {
            message = "This media's certificate has been REVOKED — treat with caution.";
        } else if (!exact) {
            message = "Perceptual match found (Hamming distance " + hammingDistance + ") — " +
                    "file appears re-compressed or resized but visually matches the certified original.";
        } else {
            message = "Exact byte-for-byte match against certified original.";
        }

        return new VerificationResult(
                exact,
                true,
                hammingDistance,
                certificate != null ? certificate.getCertificateCode() : null,
                certificate != null ? certificate.getStatus().name() : null,
                analysis != null ? analysis.getAiResult().name() : null,
                analysis != null ? analysis.getConfidence() : 0.0,
                certificate != null ? certificate.getBlockchainTxHash() : null,
                chainConfirmed,
                certificate != null ? certificate.getCreatedAt() : null,
                message
        );
    }
}
