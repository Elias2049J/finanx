import os
from dataclasses import dataclass


def _env(name: str, default: str) -> str:
    value = os.getenv(name)
    return value if value not in (None, "") else default


def _env_int(name: str, default: int) -> int:
    value = os.getenv(name)
    return int(value) if value not in (None, "") else default


class RabbitSettings:
    def __init__(self) -> None:
        self.host = _env("RABBITMQ_HOST", "localhost")
        self.port = _env_int("RABBITMQ_PORT", 5672)
        self.user = _env("RABBITMQ_USER", "guest")
        self.password = _env("RABBITMQ_PASSWORD", "guest")
        self.virtual_host = _env("RABBITMQ_VHOST", "/")
        self.scan_queue = _env("SCAN_QUEUE", "receipt.scan")
        self.result_exchange = _env("SCAN_RESULT_EXCHANGE", "receipt.scan.result")
        self.result_routing_key = _env("SCAN_RESULT_ROUTING_KEY", "receipt.scan.result")
        self.prefetch = _env_int("SCAN_PREFETCH", 1)
        self.heartbeat = _env_int("RABBITMQ_HEARTBEAT", 60)


class StorageSettings:
    def __init__(self) -> None:
        self.endpoint = _env("S3_ENDPOINT", "http://localhost:8333")
        self.region = _env("S3_REGION", "us-east-1")
        self.access_key = _env("S3_ACCESS_KEY", "")
        self.secret_key = _env("S3_SECRET_KEY", "")
        self.bucket = _env("S3_BUCKET", "finanx-images")


class OcrSettings:
    def __init__(self) -> None:
        self.language = _env("OCR_LANG", "es")
        self.engine = _env("OCR_ENGINE", "rapidocr-ppocrv6")


@dataclass(frozen=True)
class Settings:
    rabbit: RabbitSettings
    storage: StorageSettings
    ocr: OcrSettings


def load_settings() -> Settings:
    return Settings(rabbit=RabbitSettings(), storage=StorageSettings(), ocr=OcrSettings())
