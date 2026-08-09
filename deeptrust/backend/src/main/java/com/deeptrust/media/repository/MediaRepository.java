package com.deeptrust.media.repository;

import com.deeptrust.media.entity.Media;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MediaRepository extends JpaRepository<Media, Long> {
    Optional<Media> findBySha256Hash(String sha256Hash);
    List<Media> findByPerceptualHash(String perceptualHash);
}
