import logging

from .config import load_settings
from .messaging import ReceiptScanWorker
from .ocr import RapidOcrEngine
from .storage import ObjectStorageClient


def main() -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s - %(message)s")

    settings = load_settings()
    storage = ObjectStorageClient(settings.storage)
    ocr = RapidOcrEngine(settings.ocr)
    ReceiptScanWorker(settings, storage, ocr).run()


if __name__ == "__main__":
    main()
