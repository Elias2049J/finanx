-- Titulo especifico (p. ej. "Recordatorio" o "Movimiento registrado") y accion
-- de navegacion para las notificaciones push. Si el titulo es nulo se sigue
-- usando el titulo generico por tipo.
ALTER TABLE notificaciones
    ADD COLUMN titulo VARCHAR(120),
    ADD COLUMN accion VARCHAR(32);
