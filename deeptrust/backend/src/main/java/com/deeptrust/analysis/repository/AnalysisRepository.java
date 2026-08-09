package com.deeptrust.analysis.repository;

import com.deeptrust.analysis.entity.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnalysisRepository extends JpaRepository<Analysis, Long> {
    Optional<Analysis> findByMediaId(Long mediaId);
    List<Analysis> findByAiResult(com.deeptrust.analysis.entity.AnalysisResult result);
}
