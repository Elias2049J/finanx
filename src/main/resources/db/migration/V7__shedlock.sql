-- =============================================
-- V7: Tabla de ShedLock
-- =============================================
-- ShedLock usa esta tabla como lock distribuido para que
-- cada @Scheduled corra una sola vez por ventana aunque
-- haya varias instancias de la aplicación detrás del LB.
-- El esquema es el oficial de ShedLock para PostgreSQL.

CREATE TABLE IF NOT EXISTS shedlock
(
    name       VARCHAR(64)  NOT NULL,
    lock_until TIMESTAMP(3) NOT NULL,
    locked_at  TIMESTAMP(3) NOT NULL,
    locked_by  VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
