-- MIG-05.2 confirmed there are no existing image rows to backfill.
-- Abort instead of dropping any unexpected legacy image data.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM imagenes) THEN
        RAISE EXCEPTION 'MIG-05.2 requires an empty imagenes table before removing the legacy BLOB';
    END IF;
END $$;

ALTER TABLE imagenes
    ADD COLUMN IF NOT EXISTS bucket_name VARCHAR(255) NOT NULL,
    ADD COLUMN IF NOT EXISTS object_key VARCHAR(512) NOT NULL,
    ADD COLUMN IF NOT EXISTS size_bytes BIGINT NOT NULL;

ALTER TABLE imagenes
    DROP COLUMN IF EXISTS data;

CREATE UNIQUE INDEX IF NOT EXISTS ux_imagenes_bucket_object_key
    ON imagenes(bucket_name, object_key);
