ALTER TABLE public.email_verification_challenges
    ADD CONSTRAINT ck_email_verification_email_normalized
        CHECK (email = lower(btrim(email)));
