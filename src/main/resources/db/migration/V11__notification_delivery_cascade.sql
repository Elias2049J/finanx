-- P2-20: las entregas deben seguir la vida del outbox. Antes de crear la FK
-- se limpian las filas huerfanas que hayan quedado de purgas anteriores.
DELETE FROM public.notification_outbox_delivery d
WHERE NOT EXISTS (
    SELECT 1
    FROM public.notification_outbox o
    WHERE o.id = d.outbox_id
);

ALTER TABLE public.notification_outbox_delivery
    ADD CONSTRAINT fk_notification_outbox_delivery_outbox
        FOREIGN KEY (outbox_id)
        REFERENCES public.notification_outbox (id)
        ON DELETE CASCADE;
