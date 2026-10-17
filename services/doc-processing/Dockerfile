FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# libgomp1: runtime de OpenMP que requiere onnxruntime (sin él, el import falla
# con "libgomp.so.1: cannot open shared object file").
# libgl1 / libglib2.0-0: dependencias nativas de OpenCV/Pillow.
RUN apt-get update \
    && apt-get install -y --no-install-recommends libgomp1 libgl1 libglib2.0-0 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Pre-descarga de los modelos PP-OCRv6 (detección, clasificación de orientación y
# reconocimiento para español) para que el worker no dependa de la red en el
# primer trabajo y pueda operar offline. Los modelos se guardan junto al paquete
# rapidocr, que es el directorio por defecto que usa RapidOCR en runtime.
RUN rapidocr config --save_cfg_file /app/rapidocr-models.yaml \
    && python -c "from omegaconf import OmegaConf; cfg = OmegaConf.load('/app/rapidocr-models.yaml'); cfg.Rec.lang_type = 'es'; OmegaConf.save(cfg, '/app/rapidocr-models.yaml')" \
    && rapidocr download_models --config /app/rapidocr-models.yaml

COPY app ./app

CMD ["python", "-m", "app.main"]
