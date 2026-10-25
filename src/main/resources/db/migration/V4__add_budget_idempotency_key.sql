-- La entidad Budget mapea clave_idempotencia desde antes, pero V1 no la creaba.
-- La BD de desarrollo ya la tenia por drift; esta migracion la hace reproducible
-- en una base nueva y agrega la unique que protege la clave determinista del scheduler.
ALTER TABLE public.presupuestos
    ADD COLUMN IF NOT EXISTS clave_idempotencia character varying(255);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conname = 'presupuestos_clave_idempotencia_key'
           AND conrelid = 'public.presupuestos'::regclass
    ) THEN
        ALTER TABLE public.presupuestos
            ADD CONSTRAINT presupuestos_clave_idempotencia_key UNIQUE (clave_idempotencia);
    END IF;
END $$;
