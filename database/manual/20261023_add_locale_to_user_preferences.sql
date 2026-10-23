-- Aplicar manualmente antes de desplegar el backend que persiste UserPreferences.locale.
-- Hibernate tiene ddl-auto=none y no aplica cambios de esquema automáticamente.
ALTER TABLE preferencias_usuario
    ADD COLUMN IF NOT EXISTS locale VARCHAR(16) NOT NULL DEFAULT 'es-PE';
