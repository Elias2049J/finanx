-- Permite reutilizar el flujo de codigos de verificacion para restablecer
-- contrasenas sin crear una tabla ni un mecanismo de tokens paralelo.

ALTER TABLE public.email_verification_challenges
    DROP CONSTRAINT IF EXISTS ck_email_verification_purpose;

ALTER TABLE public.email_verification_challenges
    ADD CONSTRAINT ck_email_verification_purpose
        CHECK (((purpose)::text = ANY ((ARRAY[
            'REGISTRATION'::character varying,
            'EXISTING_ACCOUNT'::character varying,
            'PASSWORD_RESET'::character varying
        ])::text[])));
