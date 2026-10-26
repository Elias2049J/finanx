import json
import sys
import types
import unittest
from pathlib import Path
from types import SimpleNamespace

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

try:
    import pika  # noqa: F401
except ModuleNotFoundError:
    pika = types.ModuleType("pika")

    class AMQPError(Exception):
        pass

    class UnroutableError(AMQPError):
        pass

    class NackError(AMQPError):
        pass

    pika.exceptions = SimpleNamespace(
        AMQPError=AMQPError,
        UnroutableError=UnroutableError,
        NackError=NackError,
    )
    pika.PlainCredentials = lambda *args, **kwargs: None
    pika.ConnectionParameters = lambda *args, **kwargs: None
    pika.BasicProperties = lambda **kwargs: SimpleNamespace(**kwargs)
    pika.BlockingConnection = lambda *args, **kwargs: None
    sys.modules["pika"] = pika

# Avoid importing OCR/OpenCV and boto3 in this unit test.
ocr_module = types.ModuleType("app.ocr")
ocr_module.RapidOcrEngine = object
sys.modules["app.ocr"] = ocr_module
storage_module = types.ModuleType("app.storage")
storage_module.ObjectStorageClient = object
sys.modules["app.storage"] = storage_module

from app.config import OcrSettings, RabbitSettings, Settings, StorageSettings  # noqa: E402
from app.messaging import ReceiptScanWorker  # noqa: E402


class FakeStorage:
    def download(self, bucket, object_key):
        return b"image-bytes"


class FakeOcr:
    name = "fake-ocr"

    def recognize(self, image_bytes):
        return SimpleNamespace(raw_text="TOTAL S/ 10.00", confidence=0.91, duration_ms=12)


class FakeChannel:
    def __init__(self):
        self.exchanges = []
        self.queues = []
        self.bindings = []
        self.published = []
        self.acked = []
        self.nacked = []
        self.confirmed = False
        self.fail_publish = False

    def exchange_declare(self, exchange, exchange_type, durable):
        self.exchanges.append(exchange)

    def queue_declare(self, queue, durable, arguments=None):
        self.queues.append((queue, arguments))

    def queue_bind(self, queue, exchange, routing_key):
        self.bindings.append((queue, exchange, routing_key))

    def confirm_delivery(self):
        self.confirmed = True

    def basic_publish(self, **kwargs):
        if self.fail_publish:
            raise pika.exceptions.UnroutableError("result queue is not bound")
        self.published.append(kwargs)

    def basic_ack(self, delivery_tag):
        self.acked.append(delivery_tag)

    def basic_nack(self, delivery_tag, requeue):
        self.nacked.append((delivery_tag, requeue))


def build_worker():
    settings = Settings(
        rabbit=RabbitSettings(),
        storage=StorageSettings(),
        ocr=OcrSettings(),
    )
    return ReceiptScanWorker(settings, FakeStorage(), FakeOcr())


class ReceiptScanWorkerTest(unittest.TestCase):
    def setUp(self):
        self.worker = build_worker()
        self.channel = FakeChannel()
        self.method = SimpleNamespace(delivery_tag=7)

    def test_declares_complete_result_topology(self):
        self.worker._declare_topology(self.channel)

        self.assertEqual(
            set(self.channel.exchanges),
            {
                "receipt.scan",
                "receipt.scan.dlx",
                "receipt.scan.result",
                "receipt.scan.result.dlx",
            },
        )
        queue_names = {queue for queue, _ in self.channel.queues}
        self.assertIn("receipt.scan.result", queue_names)
        self.assertIn("receipt.scan.result.dlq", queue_names)

        result_queue_args = next(
            arguments for queue, arguments in self.channel.queues
            if queue == "receipt.scan.result"
        )
        self.assertEqual(result_queue_args["x-dead-letter-exchange"], "receipt.scan.result.dlx")
        self.assertEqual(result_queue_args["x-dead-letter-routing-key"], "receipt.scan.result.dead")

        self.assertIn(
            ("receipt.scan.result", "receipt.scan.result", "receipt.scan.result"),
            self.channel.bindings,
        )
        self.assertIn(
            ("receipt.scan.result.dlq", "receipt.scan.result.dlx", "receipt.scan.result.dead"),
            self.channel.bindings,
        )

    def test_acks_only_after_successful_publish(self):
        body = json.dumps({"receiptId": 42, "objectKey": "receipts/42.jpg"}).encode()

        self.worker._on_message(self.channel, self.method, None, body)

        self.assertEqual(len(self.channel.published), 1)
        published = self.channel.published[0]
        self.assertTrue(published["mandatory"])
        self.assertEqual(published["properties"].message_id, "42")
        self.assertEqual(self.channel.acked, [7])
        self.assertEqual(self.channel.nacked, [])

    def test_requeues_job_when_result_publish_fails(self):
        self.channel.fail_publish = True
        body = json.dumps({"receiptId": 42, "objectKey": "receipts/42.jpg"}).encode()

        self.worker._on_message(self.channel, self.method, None, body)

        self.assertEqual(self.channel.acked, [])
        self.assertEqual(self.channel.nacked, [(7, True)])

    def test_discards_malformed_job(self):
        self.worker._on_message(self.channel, self.method, None, b"not-json")

        self.assertEqual(self.channel.acked, [])
        self.assertEqual(self.channel.nacked, [(7, False)])


if __name__ == "__main__":
    unittest.main()
