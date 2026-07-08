-- =============================================
-- V5: Renombrar columnas de forma segura (fallback)
-- =============================================
-- Solo ejecuta el rename si la columna origen existe y la destino no.
-- Útil para bases de datos creadas por V1 (con id_wallet) que
-- aún no hayan sido migradas por Hibernate ddl-auto=update.

-- Renombrar id_wallet -> id_billetera en movimientos
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'movimientos' AND column_name = 'id_wallet'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'movimientos' AND column_name = 'id_billetera'
    ) THEN
        ALTER TABLE movimientos RENAME COLUMN id_wallet TO id_billetera;
    END IF;
END $$;
