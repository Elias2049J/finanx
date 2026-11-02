-- La preferencia de moneda del usuario se elimino del modelo en el refactor de
-- moneda: UserPreferences ya no mapea estas columnas. Como Hibernate corre con
-- ddl-auto=none, las columnas legadas de V1 seguian existiendo y su NOT NULL
-- rompia el INSERT de preferencias (SQLState 23502) en cualquier base creada
-- desde cero con V1.
ALTER TABLE public.preferencias_usuario
    DROP COLUMN IF EXISTS codigo_moneda,
    DROP COLUMN IF EXISTS contador_cambios_moneda,
    DROP COLUMN IF EXISTS mes_ultimo_cambio_moneda;
