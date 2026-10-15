import boto3
from botocore.config import Config

from .config import StorageSettings


class ObjectStorageClient:
    def __init__(self, settings: StorageSettings) -> None:
        self._settings = settings
        self._client = boto3.client(
            "s3",
            endpoint_url=settings.endpoint,
            region_name=settings.region,
            aws_access_key_id=settings.access_key,
            aws_secret_access_key=settings.secret_key,
            config=Config(signature_version="s3v4", s3={"addressing_style": "path"}),
        )

    def download(self, bucket: str, object_key: str) -> bytes:
        target_bucket = bucket or self._settings.bucket
        response = self._client.get_object(Bucket=target_bucket, Key=object_key)
        return response["Body"].read()
