-- V1 is missing two columns mapped by Transaction. Reconcile them for fresh
-- schemas as well as databases where they were added outside Flyway.
ALTER TABLE public.movimientos
    ADD COLUMN IF NOT EXISTS id_usuario bigint,
    ADD COLUMN IF NOT EXISTS clave_idempotencia character varying(255);

-- Existing transaction ownership is authoritative on its wallet.
UPDATE public.movimientos movimiento
   SET id_usuario = billetera.id_usuario
  FROM public.billeteras billetera
 WHERE movimiento.id_billetera = billetera.id
   AND movimiento.id_usuario IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM public.movimientos
         WHERE id_usuario IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot backfill movimientos.id_usuario from billeteras';
    END IF;

    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = 'public.movimientos'::regclass
           AND confrelid = 'public.usuarios'::regclass
           AND contype = 'f'
           AND pg_get_constraintdef(oid) LIKE 'FOREIGN KEY (id_usuario)%'
    ) THEN
        ALTER TABLE public.movimientos
            ADD CONSTRAINT fk_movimientos_usuario
            FOREIGN KEY (id_usuario) REFERENCES public.usuarios(id);
    END IF;
END $$;

ALTER TABLE public.movimientos
    ALTER COLUMN id_usuario SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS movimientos_clave_idempotencia_key
    ON public.movimientos (clave_idempotencia);

CREATE INDEX IF NOT EXISTS idx_movimientos_usuario_categoria_activo_fecha
    ON public.movimientos (id_usuario, id_categoria, activo, fecha_creacion);
