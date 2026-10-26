-- Borrado programado de cuenta: 7 dias de gracia antes de eliminar todos los datos.
ALTER TABLE public.usuarios
    ADD COLUMN IF NOT EXISTS solicitud_eliminacion_en TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS eliminacion_programada_en TIMESTAMP WITH TIME ZONE;

CREATE INDEX IF NOT EXISTS idx_usuarios_eliminacion_programada
    ON public.usuarios (eliminacion_programada_en)
    WHERE eliminacion_programada_en IS NOT NULL;
