-- Columnas que hasta ahora se aplicaban a mano (database/manual).
-- Se versionan para que una base limpia quede identica a la de desarrollo.
ALTER TABLE preferencias_usuario
    ADD COLUMN IF NOT EXISTS locale VARCHAR(16) NOT NULL DEFAULT 'es-PE';

ALTER TABLE notificaciones
    ADD COLUMN IF NOT EXISTS locale VARCHAR(16) NOT NULL DEFAULT 'es-PE';
