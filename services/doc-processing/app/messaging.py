import json
import logging
import time

import pika

from .config import Settings
from .ocr import RapidOcrEngine
from .storage import ObjectStorageClient

logger = logging.getLogger("doc-processing")

RECONNECT_DELAY_SECONDS = 5


class ReceiptScanWorker:
    def __init__(self, settings: Settings, storage: ObjectStorageClient, ocr: RapidOcrEngine) -> None:
        self._settings = settings
        self._storage = storage
        self._ocr = ocr

    def run(self) -> None:
        while True:
            try:
                self._consume()
            except pika.exceptions.AMQPError as exc:
                logger.warning("RabbitMQ connection lost (%s); reconnecting in %ss", exc, RECONNECT_DELAY_SECONDS)
                time.sleep(RECONNECT_DELAY_SECONDS)

    def _consume(self) -> None:
        rabbit = self._settings.rabbit
        credentials = pika.PlainCredentials(rabbit.user, rabbit.password)
        parameters = pika.ConnectionParameters(
            host=rabbit.host,
            port=rabbit.port,
            virtual_host=rabbit.virtual_host,
            credentials=credentials,
            heartbeat=rabbit.heartbeat,
        )
        connection = pika.BlockingConnection(parameters)
        try:
            channel = connection.channel()
            self._declare_topology(channel)
            channel.confirm_delivery()
            channel.basic_qos(prefetch_count=rabbit.prefetch)
            channel.basic_consume(
                queue=rabbit.scan_queue,
                on_message_callback=self._on_message,
                auto_ack=False,
            )
            logger.info("Waiting for scan jobs on queue '%s'", rabbit.scan_queue)
            channel.start_consuming()
        finally:
            if connection.is_open:
                connection.close()

    def _declare_topology(self, channel) -> None:
        rabbit = self._settings.rabbit
        for exchange in (rabbit.scan_exchange, rabbit.scan_dlx,
                         rabbit.result_exchange, rabbit.result_dlx):
            channel.exchange_declare(exchange=exchange, exchange_type="direct", durable=True)

        channel.queue_declare(
            queue=rabbit.scan_queue,
            durable=True,
            arguments={
                "x-dead-letter-exchange": rabbit.scan_dlx,
                "x-dead-letter-routing-key": rabbit.scan_dlq_routing_key,
            },
        )
        channel.queue_declare(queue=rabbit.scan_dlq, durable=True)
        channel.queue_declare(
            queue=rabbit.result_queue,
            durable=True,
            arguments={
                "x-dead-letter-exchange": rabbit.result_dlx,
                "x-dead-letter-routing-key": rabbit.result_dlq_routing_key,
            },
        )
        channel.queue_declare(queue=rabbit.result_dlq, durable=True)

        channel.queue_bind(
            queue=rabbit.scan_queue,
            exchange=rabbit.scan_exchange,
            routing_key=rabbit.scan_routing_key,
        )
        channel.queue_bind(
            queue=rabbit.scan_dlq,
            exchange=rabbit.scan_dlx,
            routing_key=rabbit.scan_dlq_routing_key,
        )
        channel.queue_bind(
            queue=rabbit.result_queue,
            exchange=rabbit.result_exchange,
            routing_key=rabbit.result_routing_key,
        )
        channel.queue_bind(
            queue=rabbit.result_dlq,
            exchange=rabbit.result_dlx,
            routing_key=rabbit.result_dlq_routing_key,
        )

    def _on_message(self, channel, method, properties, body) -> None:
        try:
            payload = json.loads(body)
            receipt_id = int(payload["receiptId"])
            object_key = payload["objectKey"]
            bucket = payload.get("bucket")
        except (ValueError, KeyError, TypeError):
            logger.error("Discarding malformed scan job: %r", body)
            channel.basic_nack(method.delivery_tag, requeue=False)
            return

        result = self._process(receipt_id, bucket, object_key)
        try:
            self._publish_result(channel, result)
        except Exception as exc:  # noqa: BLE001 - never ACK a result that was not published
            logger.error(
                "scan_result_publish_failed receipt_id=%s; requeueing job",
                receipt_id,
                exc_info=exc,
            )
            channel.basic_nack(method.delivery_tag, requeue=True)
            return

        channel.basic_ack(method.delivery_tag)
        logger.info("scan_job_processed receipt_id=%s status=%s", receipt_id, result["status"])

    def _process(self, receipt_id: int, bucket, object_key: str) -> dict:
        try:
            image_bytes = self._storage.download(bucket, object_key)
            ocr_result = self._ocr.recognize(image_bytes)
            return {
                "receiptId": receipt_id,
                "status": "DONE",
                "rawText": ocr_result.raw_text,
                "engine": self._ocr.name,
                "engineConfidence": round(ocr_result.confidence, 4),
                "durationMs": ocr_result.duration_ms,
                "error": None,
            }
        except Exception as exc:  # noqa: BLE001 - the failure is reported to the core, not swallowed
            logger.exception("scan_job_failed receipt_id=%s", receipt_id)
            return {
                "receiptId": receipt_id,
                "status": "FAILED",
                "rawText": None,
                "engine": self._ocr.name,
                "engineConfidence": None,
                "durationMs": 0,
                "error": str(exc),
            }

    def _publish_result(self, channel, result: dict) -> None:
        rabbit = self._settings.rabbit
        channel.basic_publish(
            exchange=rabbit.result_exchange,
            routing_key=rabbit.result_routing_key,
            body=json.dumps(result).encode("utf-8"),
            properties=pika.BasicProperties(
                delivery_mode=2,
                content_type="application/json",
                message_id=str(result["receiptId"]),
            ),
            mandatory=True,
        )
