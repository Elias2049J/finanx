-- =============================================
-- UPGRADE: Migración de Transaction → Wallet
-- =============================================
-- Este script es IDEMPOTENTE: funciona tanto en
-- bases de datos nuevas como existentes.

-- FASE 0: Asegurar que las tablas existan
CREATE TABLE IF NOT EXISTS billeteras (
    id              BIGSERIAL PRIMARY KEY,
    id_usuario      BIGINT NOT NULL REFERENCES usuarios(id),
    moneda          VARCHAR(3) NOT NULL,
    balance         NUMERIC(19,4) NOT NULL DEFAULT 0,
    es_principal    BOOLEAN NOT NULL DEFAULT false,
    version         BIGINT DEFAULT 0
);

-- FASE 1: Crear wallet default PEN para cada usuario
INSERT INTO billeteras (id_usuario, moneda, balance, es_principal)
SELECT u.id, 'PEN', 0, true
FROM usuarios u
WHERE NOT EXISTS (
    SELECT 1 FROM billeteras w WHERE w.id_usuario = u.id
);

-- FASE 2: Agregar columna id_wallet a movimientos
ALTER TABLE movimientos
    ADD COLUMN IF NOT EXISTS id_wallet BIGINT;

-- FASE 3: Asignar wallet default a cada transacción
UPDATE movimientos t
SET id_wallet = (
    SELECT w.id FROM billeteras w
    WHERE w.id_usuario = t.id_usuario
      AND w.es_principal = true
)
WHERE t.id_wallet IS NULL;

-- FASE 4: Hacer id_wallet NOT NULL
ALTER TABLE movimientos
    ALTER COLUMN id_wallet SET NOT NULL;

-- FASE 5: Agregar FK y restricciones
ALTER TABLE movimientos
    ADD CONSTRAINT fk_movimientos_wallet
    FOREIGN KEY (id_wallet) REFERENCES billeteras(id);

CREATE INDEX IF NOT EXISTS idx_movimientos_wallet
    ON movimientos(id_wallet);

-- FASE 6: Recalcular balances de wallets
UPDATE billeteras w
SET balance = (
    SELECT COALESCE(SUM(
        CASE WHEN t.tipo = 'INCOME' THEN t.monto ELSE -t.monto END
    ), 0)
    FROM movimientos t
    WHERE t.id_wallet = w.id AND t.activo = true
);
