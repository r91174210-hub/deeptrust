package com.deeptrust.analysis.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "analysis")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long mediaId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisResult aiResult; // AUTHENTIC | MANIPULATED | INCONCLUSIVE

    @Column(nullable = false)
    private double confidence;

    // JSON-serialized array of {x, y, w, h, score} regions flagged by the model.
    @Lob
    @Column(columnDefinition = "TEXT")
    private String heatmapCoordinatesJson;

    // Feature #6/#4 tie-in: human review can override the AI verdict for
    // INCONCLUSIVE cases without destroying the original AI output.
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private AnalysisResult humanReviewResult;

    private Long reviewedByUserId;

    private Instant reviewedAt;

    @Column(nullable = false, length = 50)
    private String modelVersion; // reproducibility — which model produced this verdict

    @Column(nullable = false)
    private Instant analyzedAt;
}
