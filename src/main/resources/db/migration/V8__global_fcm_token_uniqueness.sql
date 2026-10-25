-- A token already assigned to multiple accounts cannot be transferred safely
-- by migration. Resolve those groups explicitly before applying this version.
DO $$
DECLARE
    duplicate_groups integer;
BEGIN
    SELECT count(*)
      INTO duplicate_groups
      FROM (
          SELECT token
            FROM public.fcm_tokens
           GROUP BY token
          HAVING count(DISTINCT user_id) > 1
      ) duplicates;

    IF duplicate_groups > 0 THEN
        RAISE EXCEPTION 'Found % FCM tokens shared across users; resolve them before V8', duplicate_groups;
    END IF;
END $$;

ALTER TABLE public.fcm_tokens
    DROP CONSTRAINT uq_fcm_token_user_token;

DROP INDEX IF EXISTS public.idx_fcm_token_token;

ALTER TABLE public.fcm_tokens
    ADD CONSTRAINT uq_fcm_tokens_token UNIQUE (token);
