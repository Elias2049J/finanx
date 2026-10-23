-- Locale snapshot for each notification; existing messages default to Spanish.
-- Hibernate has ddl-auto=none and does not apply schema changes automatically.
ALTER TABLE notificaciones
    ADD COLUMN IF NOT EXISTS locale VARCHAR(16) NOT NULL DEFAULT 'es-PE';
