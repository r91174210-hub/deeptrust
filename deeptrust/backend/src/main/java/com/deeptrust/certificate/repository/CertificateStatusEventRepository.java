package com.deeptrust.certificate.repository;

import com.deeptrust.certificate.entity.CertificateStatusEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateStatusEventRepository extends JpaRepository<CertificateStatusEvent, Long> {
    List<CertificateStatusEvent> findByCertificateIdOrderByOccurredAtAsc(Long certificateId);
}
