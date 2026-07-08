-- =============================================
-- V6: Renombrar columnas inglés → español
-- =============================================
-- Hibernate creó el schema inicial con nombres en inglés.
-- Las entidades se actualizaron a español pero no hubo
-- migración que renombrara las columnas existentes.
-- Hibernate ddl-auto=update NO renombra; solo ADD.
-- Por eso fallaba al intentar ADD COLUMN ... NOT NULL
-- sobre filas existentes.

-- metas_ahorro: description → descripcion
ALTER TABLE IF EXISTS metas_ahorro
    RENAME COLUMN description TO descripcion;

-- metas_ahorro: target_amount → monto_objetivo
ALTER TABLE IF EXISTS metas_ahorro
    RENAME COLUMN target_amount TO monto_objetivo;

-- reglas_recurrencia: fecha_fin fue añadida por Hibernate
-- como columna vacía (nullable) junto a la original end_date.
-- La eliminamos antes de renombrar end_date.
ALTER TABLE IF EXISTS reglas_recurrencia
    DROP COLUMN IF EXISTS fecha_fin;

-- reglas_recurrencia: end_date → fecha_fin
ALTER TABLE IF EXISTS reglas_recurrencia
    RENAME COLUMN end_date TO fecha_fin;

-- reglas_recurrencia: recurrence_type → tipo_recurrencia
ALTER TABLE IF EXISTS reglas_recurrencia
    RENAME COLUMN recurrence_type TO tipo_recurrencia;

-- reglas_recurrencia: interval_value → valor_intervalo
ALTER TABLE IF EXISTS reglas_recurrencia
    RENAME COLUMN interval_value TO valor_intervalo;

-- reglas_recurrencia: start_date → fecha_inicio
ALTER TABLE IF EXISTS reglas_recurrencia
    RENAME COLUMN start_date TO fecha_inicio;
