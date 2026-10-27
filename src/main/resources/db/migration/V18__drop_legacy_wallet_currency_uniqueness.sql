DO $$
DECLARE
    user_column SMALLINT;
    currency_column SMALLINT;
    unique_constraint RECORD;
BEGIN
    SELECT attnum
    INTO user_column
    FROM pg_attribute
    WHERE attrelid = 'public.billeteras'::regclass
      AND attname = 'id_usuario'
      AND NOT attisdropped;

    SELECT attnum
    INTO currency_column
    FROM pg_attribute
    WHERE attrelid = 'public.billeteras'::regclass
      AND attname = 'moneda'
      AND NOT attisdropped;

    FOR unique_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'public.billeteras'::regclass
          AND contype = 'u'
          AND cardinality(conkey) = 2
          AND conkey @> ARRAY[user_column, currency_column]
    LOOP
        EXECUTE format(
                'ALTER TABLE public.billeteras DROP CONSTRAINT %I',
                unique_constraint.conname);
    END LOOP;
END $$;
