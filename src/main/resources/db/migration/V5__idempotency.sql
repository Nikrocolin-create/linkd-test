--drop constraint because of expires_at;
ALTER TABLE links DROP CONSTRAINT long_url_unique;
ALTER TABLE links DROP CONSTRAINT links_short_code_key;

CREATE TABLE public.idempotency(
    id UUID NOT NULL,
    response JSONB NOT NULL,
	created_at timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
	expires_at timestamptz(6) NOT NULL DEFAULT '2099-12-31',
	CONSTRAINT idempotency_pkey PRIMARY KEY (id)
);