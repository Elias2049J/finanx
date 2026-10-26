-- P2-19: reclamo temporal del dispatcher. El estado sigue siendo PENDING;
-- claimed_until solo evita que dos instancias publiquen la misma fila.
ALTER TABLE public.notification_outbox
    ADD COLUMN claimed_until timestamp(6) with time zone;

CREATE INDEX idx_notification_outbox_pending_claim
    ON public.notification_outbox (id)
    WHERE state = 'PENDING';
