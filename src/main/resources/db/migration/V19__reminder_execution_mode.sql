-- Recordatorios accionables: modo de ejecucion y ocurrencia pendiente de confirmacion.
ALTER TABLE public.programacion
    ADD COLUMN IF NOT EXISTS modo_ejecucion VARCHAR(20) NOT NULL DEFAULT 'MANUAL';

ALTER TABLE public.programacion
    DROP CONSTRAINT IF EXISTS programacion_estado_check;

ALTER TABLE public.programacion
    ADD CONSTRAINT programacion_estado_check
    CHECK (estado IN ('RUNNING', 'PAUSED', 'FINALIZED', 'CANCELLED', 'CREATED', 'ERROR', 'PENDING_CONFIRMATION'));
