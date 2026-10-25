-- Separate the delivery lease/retry metadata from broker state. Existing
-- deliveries receive a deterministic key so retries keep the same identity.
ALTER TABLE public.notification_outbox
    ADD COLUMN retry_count integer NOT NULL DEFAULT 0,
    ADD COLUMN next_retry_at timestamp(6) with time zone,
    ADD CONSTRAINT notification_outbox_retry_count_check
        CHECK (retry_count BETWEEN 0 AND 5);

ALTER TABLE public.notification_outbox_delivery
    ADD COLUMN attempt_count integer NOT NULL DEFAULT 0,
    ADD COLUMN sending_started_at timestamp(6) with time zone,
    ADD COLUMN idempotency_key character varying(128);

UPDATE public.notification_outbox_delivery
   SET idempotency_key = 'notification-outbox:' || outbox_id || ':' || channel;

ALTER TABLE public.notification_outbox_delivery
    ALTER COLUMN idempotency_key SET NOT NULL,
    ADD CONSTRAINT notification_outbox_delivery_attempt_count_check
        CHECK (attempt_count >= 0),
    ADD CONSTRAINT uq_notification_outbox_delivery_idempotency_key
        UNIQUE (idempotency_key);

ALTER TABLE public.notification_outbox_delivery
    DROP CONSTRAINT notification_outbox_delivery_state_check;

ALTER TABLE public.notification_outbox_delivery
    ADD CONSTRAINT notification_outbox_delivery_state_check
        CHECK (state IN ('PENDING', 'SENDING', 'SENT', 'SKIPPED', 'FAILED'));

CREATE INDEX idx_notification_outbox_retry_due
    ON public.notification_outbox (next_retry_at, id)
    WHERE state = 'FAILED' AND next_retry_at IS NOT NULL;
