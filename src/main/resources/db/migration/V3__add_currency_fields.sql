-- =============================================
-- V3: Agregar campos multi-monedas (idempotente)
-- =============================================

-- SavingGoal: código de moneda y monto congelado
ALTER TABLE metas_ahorro
    ADD COLUMN IF NOT EXISTS codigo_moneda VARCHAR(3) NOT NULL DEFAULT 'PEN',
    ADD COLUMN IF NOT EXISTS monto_acumulado_congelado NUMERIC(19,2);

-- Budget: código de moneda y monto congelado
ALTER TABLE presupuestos
    ADD COLUMN IF NOT EXISTS codigo_moneda VARCHAR(3) NOT NULL DEFAULT 'PEN',
    ADD COLUMN IF NOT EXISTS monto_gastado_congelado NUMERIC(19,2);

-- TransactionSchedule: relación obligatoria con billetera
ALTER TABLE programacion_movimientos
    ADD COLUMN IF NOT EXISTS id_billetera BIGINT;

UPDATE programacion_movimientos pm
SET id_billetera = (
    SELECT w.id FROM billeteras w
    JOIN usuarios u ON u.id = w.id_usuario
    WHERE pm.id_usuario = u.id AND w.es_principal = true
)
WHERE pm.id_billetera IS NULL;

ALTER TABLE programacion_movimientos
    ALTER COLUMN id_billetera SET NOT NULL;

ALTER TABLE programacion_movimientos
    ADD CONSTRAINT fk_programacion_billetera
    FOREIGN KEY (id_billetera) REFERENCES billeteras(id);
