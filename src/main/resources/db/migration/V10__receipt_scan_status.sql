-- Estado del escaneo OCR asíncrono del comprobante.
-- Los comprobantes existentes se crearon de forma síncrona, por lo que quedan DONE.
ALTER TABLE comprobantes ADD COLUMN estado_scan varchar(20);
UPDATE comprobantes SET estado_scan = 'DONE';
ALTER TABLE comprobantes ALTER COLUMN estado_scan SET NOT NULL;
ALTER TABLE comprobantes ALTER COLUMN estado_scan SET DEFAULT 'PENDING';

ALTER TABLE comprobantes ADD COLUMN error_scan TEXT;
