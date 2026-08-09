package com.deeptrust.certificate.repository;

import com.deeptrust.certificate.entity.Certificate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {
    Optional<Certificate> findByCertificateCode(String certificateCode);
    Optional<Certificate> findByMediaId(Long mediaId);
    long countByCreatedAtBetween(java.time.Instant start, java.time.Instant end);
}
