package com.deeptrust.certificate.controller;

import com.deeptrust.certificate.dto.CertificateDtos.CertificateResponse;
import com.deeptrust.certificate.dto.CertificateDtos.RevokeRequest;
import com.deeptrust.certificate.dto.CertificateDtos.RevokeResponse;
import com.deeptrust.certificate.service.CertificateRevocationService;
import com.deeptrust.certificate.service.CertificateService;
import com.deeptrust.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/certificates")
@RequiredArgsConstructor
public class CertificateController {

    private final CertificateService certificateService;
    private final CertificateRevocationService revocationService;

    @PostMapping("/issue")
    @PreAuthorize("hasAnyRole('ANALYST','INVESTIGATOR','ROOT')")
    public ResponseEntity<CertificateResponse> issue(@RequestParam Long mediaId,
                                                       @AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(certificateService.issueCertificate(mediaId, user.getId()));
    }

    @PostMapping("/{certificateCode}/revoke")
    @PreAuthorize("hasAnyRole('INVESTIGATOR','ROOT')")
    public ResponseEntity<RevokeResponse> revoke(@PathVariable String certificateCode,
                                                  @Valid @RequestBody RevokeRequest request,
                                                  @AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(revocationService.revoke(certificateCode, request.reason(), user.getId()));
    }
}
