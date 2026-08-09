package com.deeptrust.verify.controller;

import com.deeptrust.security.CurrentUser;
import com.deeptrust.verify.dto.VerificationResult;
import com.deeptrust.verify.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerificationController {

    private final VerificationService verificationService;

    @PostMapping(value = "/file", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('VIEWER','OPERATOR','ANALYST','INVESTIGATOR','ROOT')")
    public ResponseEntity<VerificationResult> verifyFile(@RequestParam("file") MultipartFile file,
                                                           @AuthenticationPrincipal CurrentUser user) {
        return ResponseEntity.ok(verificationService.verifyUploadedFile(file, user.getId()));
    }
}
