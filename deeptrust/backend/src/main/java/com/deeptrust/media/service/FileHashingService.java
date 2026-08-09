package com.deeptrust.media.service;

import dev.brachtendorf.jimagehash.hash.Hash;
import dev.brachtendorf.jimagehash.hashAlgorithms.PerceptiveHash;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class FileHashingService {

    // 64-bit perceptual hash — good balance of sensitivity vs. false-positive
    // rate for compression/resize-resistant matching.
    private final PerceptiveHash perceptiveHash = new PerceptiveHash(64);

    /** Exact-byte SHA-256 — changes with even a single-bit modification. */
    public String sha256(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read file for hashing", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    /**
     * Perceptual hash for images/video keyframes — tolerant of re-compression,
     * resizing, and minor color adjustments. Returns null for non-visual
     * media (pure audio), where pHash doesn't apply.
     */
    public String perceptualHash(MultipartFile file) {
        String mimeType = file.getContentType();
        if (mimeType == null || !mimeType.startsWith("image/")) {
            // Video pHash would be computed frame-by-frame by the AI
            // microservice (Module 3) and passed back in the analysis
            // payload; this method covers the direct-image upload path.
            return null;
        }
        try (InputStream in = new ByteArrayInputStream(file.getBytes())) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IllegalArgumentException("Unable to decode image for perceptual hashing");
            }
            Hash hash = perceptiveHash.hash(image);
            return hash.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to compute perceptual hash", e);
        }
    }

    /** Hamming distance between two pHash strings — used by verification to score similarity. */
    public int hammingDistance(String hashA, String hashB) {
        Hash a = Hash.fromString(hashA);
        Hash b = Hash.fromString(hashB);
        return a.hammingDistance(b);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
