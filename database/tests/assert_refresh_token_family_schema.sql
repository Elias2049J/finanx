DO $$
BEGIN
    IF to_regclass('public.refresh_tokens') IS NULL THEN
        RAISE EXCEPTION 'refresh_tokens table is missing; apply Flyway migrations';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_index i
        WHERE i.indrelid = 'public.refresh_tokens'::regclass
          AND i.indisunique
          AND pg_get_indexdef(i.indexrelid) ILIKE '%(family_id)%'
          AND pg_get_expr(i.indpred, i.indrelid) ILIKE '%revocado_en IS NULL%'
    ) THEN
        RAISE EXCEPTION 'unique partial index for active refresh-token families is missing';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM public.refresh_tokens
        WHERE revocado_en IS NULL
        GROUP BY family_id
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'duplicate active refresh-token families found';
    END IF;
END
$$;
