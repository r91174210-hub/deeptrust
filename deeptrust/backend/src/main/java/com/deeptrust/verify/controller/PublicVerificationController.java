package com.deeptrust.verify.controller;

import com.deeptrust.verify.dto.VerificationResult;
import com.deeptrust.verify.service.VerificationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Feature #7: Public, no-authentication verification endpoint. Every issued
 * certificate gets a QR code encoding a URL to this endpoint, so anyone —
 * including non-technical viva panel members — can scan and instantly see
 * a certificate's live status pulled from both MySQL and the blockchain.
 *
 * Deliberately read-only and rate-limited since it requires no login.
 */
@RestController
@RequestMapping("/api/public/verify")
@RequiredArgsConstructor
public class PublicVerificationController {

    private final VerificationService verificationService;
    private final PublicEndpointRateLimiter rateLimiter;

    @GetMapping("/{certificateCode}")
    public ResponseEntity<?> verifyByCode(@PathVariable String certificateCode, HttpServletRequest request) {
        String clientIp = resolveClientIp(request);

        if (!rateLimiter.tryAcquire(clientIp)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Too many verification requests. Please try again in a minute.");
        }

        VerificationResult result = verificationService.verifyByCertificateCode(certificateCode);
        return ResponseEntity.ok(result);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
