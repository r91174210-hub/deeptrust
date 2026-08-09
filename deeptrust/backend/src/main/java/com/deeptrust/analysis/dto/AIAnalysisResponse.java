package com.deeptrust.analysis.dto;

import java.util.List;

/** Mirrors the JSON contract returned by the Python AI inference microservice. */
public record AIAnalysisResponse(
        String result,        // "AUTHENTIC" | "MANIPULATED" | "INCONCLUSIVE"
        double confidence,
        String pHash,
        List<HeatmapRegion> heatmapCoordinates,
        String modelVersion
) {
    public record HeatmapRegion(int x, int y, int w, int h, double score) {}
}
