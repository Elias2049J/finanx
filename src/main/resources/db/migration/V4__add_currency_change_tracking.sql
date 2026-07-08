-- =============================================
-- V4: Seguimiento de cambios de moneda
-- =============================================

ALTER TABLE preferencias_usuario
    ADD COLUMN IF NOT EXISTS contador_cambios_moneda INTEGER,
    ADD COLUMN IF NOT EXISTS mes_ultimo_cambio_moneda INTEGER;
