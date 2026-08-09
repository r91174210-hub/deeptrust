"""
DeepTrust AI Inference Microservice
------------------------------------
Isolated FastAPI service that receives media files and returns a deepfake
analysis verdict. This service is deliberately stateless and has NO direct
access to MySQL or the blockchain — it only ever talks to the Java backend
over this REST contract, preserving the trust boundary described in the
architecture doc.

Response contract (must match com.deeptrust.analysis.dto.AIAnalysisResponse):
{
  "result": "AUTHENTIC" | "MANIPULATED" | "INCONCLUSIVE",
  "confidence": float,
  "pHash": string,
  "heatmapCoordinates": [{"x": int, "y": int, "w": int, "h": int, "score": float}, ...],
  "modelVersion": string
}
"""

from fastapi import FastAPI, UploadFile, File, HTTPException
from pydantic import BaseModel
from typing import List, Optional
import io
import logging

from app.inference import run_deepfake_detection
from app.hashing import compute_perceptual_hash

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("deeptrust-ai")

app = FastAPI(
    title="DeepTrust AI Inference Service",
    description="Isolated multimodal deepfake detection microservice",
    version="1.0.0",
)

MODEL_VERSION = "deepfake-ensemble-v1.0.0"

ALLOWED_CONTENT_TYPES = {
    "image/jpeg", "image/png", "image/webp",
    "video/mp4", "video/quicktime",
    "audio/mpeg", "audio/wav",
}

MAX_FILE_SIZE_BYTES = 200 * 1024 * 1024  # 200MB, mirrors the Java-side limit


class HeatmapRegion(BaseModel):
    x: int
    y: int
    w: int
    h: int
    score: float


class AnalysisResponse(BaseModel):
    result: str
    confidence: float
    pHash: Optional[str] = None
    heatmapCoordinates: List[HeatmapRegion] = []
    modelVersion: str


@app.get("/health")
def health_check():
    return {"status": "ok", "modelVersion": MODEL_VERSION}


@app.post("/api/v1/analyze", response_model=AnalysisResponse)
async def analyze(file: UploadFile = File(...)):
    if file.content_type not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(status_code=415, detail=f"Unsupported content type: {file.content_type}")

    content = await file.read()
    if len(content) > MAX_FILE_SIZE_BYTES:
        raise HTTPException(status_code=413, detail="File exceeds maximum allowed size.")
    if len(content) == 0:
        raise HTTPException(status_code=400, detail="Empty file.")

    try:
        result, confidence, heatmap_regions = run_deepfake_detection(content, file.content_type)
    except Exception as exc:
        logger.exception("Inference failed")
        raise HTTPException(status_code=500, detail="Inference pipeline failed.") from exc

    phash = None
    if file.content_type.startswith("image/"):
        try:
            phash = compute_perceptual_hash(content)
        except Exception:
            logger.warning("pHash computation failed; continuing without it.")

    return AnalysisResponse(
        result=result,
        confidence=confidence,
        pHash=phash,
        heatmapCoordinates=[HeatmapRegion(**r) for r in heatmap_regions],
        modelVersion=MODEL_VERSION,
    )
