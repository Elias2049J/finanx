-- =============================================
-- V2: Renombrar tablas y columnas (idempotente)
-- =============================================

-- Renombrar user_preferences -> preferencias_usuario
ALTER TABLE IF EXISTS user_preferences
    RENAME TO preferencias_usuario;

-- Renombrar id_wallet -> id_billetera en movimientos
ALTER TABLE IF EXISTS movimientos
    RENAME COLUMN id_wallet TO id_billetera;
