ALTER TABLE public.billeteras
    ADD COLUMN alias VARCHAR(80);

UPDATE public.billeteras
SET alias = CASE
    WHEN es_principal THEN 'Principal'
    ELSE 'Wallet ' || moneda
END;

ALTER TABLE public.billeteras
    ALTER COLUMN alias SET NOT NULL;

ALTER TABLE public.billeteras
    DROP CONSTRAINT IF EXISTS billeteras_id_usuario_moneda_key;

CREATE INDEX IF NOT EXISTS idx_billeteras_id_usuario_moneda
    ON public.billeteras (id_usuario, moneda);
