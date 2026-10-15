import io
import time
from dataclasses import dataclass

import numpy as np
from PIL import Image
from rapidocr import RapidOCR

from .config import OcrSettings


@dataclass(frozen=True)
class OcrResult:
    raw_text: str
    confidence: float
    duration_ms: int


class RapidOcrEngine:
    def __init__(self, settings: OcrSettings) -> None:
        self._engine_name = settings.engine
        self._engine = RapidOCR(params={"Rec.lang_type": settings.language})

    @property
    def name(self) -> str:
        return self._engine_name

    def recognize(self, image_bytes: bytes) -> OcrResult:
        started = time.monotonic()
        image = np.asarray(Image.open(io.BytesIO(image_bytes)).convert("RGB"))
        output = self._engine(image)

        texts = output.txts if output.txts else ()
        scores = output.scores if output.scores else ()
        raw_text = "\n".join(texts)
        confidence = float(np.mean(scores)) if len(scores) else 0.0
        duration_ms = int((time.monotonic() - started) * 1000)

        return OcrResult(raw_text=raw_text, confidence=confidence, duration_ms=duration_ms)
