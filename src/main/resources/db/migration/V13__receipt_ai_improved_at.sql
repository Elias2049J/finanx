-- P2-23: la mejora con IA queda registrada con su propio timestamp.
-- No se rellena para filas historicas: un recibo antiguo con motor GEMINI
-- sigue mostrandose como mejorado, pero sin fecha exacta de mejora.
ALTER TABLE public.comprobantes
    ADD COLUMN ai_improved_at timestamp(6) with time zone;
