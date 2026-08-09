package com.deeptrust.media.controller;

import com.deeptrust.media.dto.MediaUploadResponse;
import com.deeptrust.media.service.MediaService;
import com.deeptrust.security.CurrentUser;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaUploadController {

    private final MediaService mediaService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OPERATOR','ANALYST','INVESTIGATOR','ROOT')")
    public ResponseEntity<MediaUploadResponse> uploadMedia(
            @RequestParam("file") @NotNull MultipartFile file,
            @AuthenticationPrincipal CurrentUser currentUser
    ) {
        MediaUploadResponse response = mediaService.processUpload(file, currentUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
