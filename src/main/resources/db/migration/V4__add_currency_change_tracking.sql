-- =============================================
-- V4: Seguimiento de cambios de moneda
-- =============================================
-- Crea la tabla si no existe (puede no haber sido creada por Hibernate
-- si la entidad UserPreferences fue añadida después del schema inicial).

CREATE TABLE IF NOT EXISTS preferencias_usuario (
    id                          BIGSERIAL PRIMARY KEY,
    id_usuario                  BIGINT NOT NULL UNIQUE REFERENCES usuarios(id),
    zona_horaria                VARCHAR(50) NOT NULL,
    codigo_moneda               VARCHAR(3) NOT NULL,
    notificaciones_email        BOOLEAN NOT NULL,
    contador_cambios_moneda     INTEGER,
    mes_ultimo_cambio_moneda    INTEGER,
    fecha_creacion              TIMESTAMP WITHOUT TIME ZONE NOT NULL,
    fecha_actualizacion         TIMESTAMP WITHOUT TIME ZONE NOT NULL
);

-- Agrega columnas de seguimiento si la tabla ya existía pero sin ellas
ALTER TABLE preferencias_usuario
    ADD COLUMN IF NOT EXISTS contador_cambios_moneda INTEGER,
    ADD COLUMN IF NOT EXISTS mes_ultimo_cambio_moneda INTEGER;
