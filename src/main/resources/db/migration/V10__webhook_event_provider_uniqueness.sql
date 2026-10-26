-- El id de evento externo solo es unico dentro de cada proveedor.
-- Antes la restriccion era global y dos proveedores no podian reutilizar el mismo id.
ALTER TABLE public.eventos_webhook
    DROP CONSTRAINT IF EXISTS eventos_webhook_id_evento_externo_key;

ALTER TABLE public.eventos_webhook
    ADD CONSTRAINT eventos_webhook_proveedor_id_evento_externo_key
        UNIQUE (proveedor, id_evento_externo);
