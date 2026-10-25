-- Refuse to guess how duplicate accounts should be merged. Resolve any groups
-- returned by the diagnostic query before retrying this migration.
DO $$
DECLARE
    duplicate_groups integer;
BEGIN
    SELECT count(*)
      INTO duplicate_groups
      FROM (
          SELECT lower(btrim(email)) AS normalized_email
            FROM public.usuarios
           GROUP BY lower(btrim(email))
          HAVING count(*) > 1
      ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION 'Found % duplicate normalized email groups; resolve them before V6', duplicate_groups;
    END IF;
END $$;

UPDATE public.usuarios
   SET email = lower(btrim(email))
 WHERE email <> lower(btrim(email));

CREATE UNIQUE INDEX IF NOT EXISTS uq_usuarios_email_lower
    ON public.usuarios (lower(email));
