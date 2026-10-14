CREATE TABLE IF NOT EXISTS notification_outbox_delivery (
    id BIGSERIAL PRIMARY KEY,
    outbox_id BIGINT NOT NULL REFERENCES notification_outbox(id) ON DELETE CASCADE,
    channel VARCHAR(24) NOT NULL,
    state VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP WITH TIME ZONE,
    error_message VARCHAR(255),
    CONSTRAINT uq_notification_outbox_delivery_channel UNIQUE (outbox_id, channel)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_delivery_outbox
    ON notification_outbox_delivery(outbox_id);
