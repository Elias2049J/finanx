-- Identidad federada: el sub de Google es el identificador estable; el email no lo es.
-- Las cuentas existentes quedan como LOCAL y conservan su login por contrasena.
ALTER TABLE usuarios
    ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(16) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS provider_user_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_provider_user
    ON usuarios (auth_provider, provider_user_id)
    WHERE provider_user_id IS NOT NULL;
