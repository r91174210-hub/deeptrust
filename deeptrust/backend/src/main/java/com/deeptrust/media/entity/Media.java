package com.deeptrust.media.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "media", indexes = {
        @Index(name = "idx_media_sha256", columnList = "sha256Hash", unique = true),
        @Index(name = "idx_media_phash", columnList = "perceptualHash")
})
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sha256Hash; // exact-byte integrity

    @Column(nullable = false, length = 64)
    private String perceptualHash; // pHash — survives re-compression/resizing

    @Column(nullable = false, length = 255)
    private String originalFilename;

    @Column(nullable = false, length = 100)
    private String mimeType;

    @Column(nullable = false)
    private long fileSizeBytes;

    @Column(nullable = false, length = 500)
    private String storagePath;

    @Column(nullable = false)
    private Long uploadedByUserId;

    @Column(nullable = false)
    private Instant uploadedAt;
}
