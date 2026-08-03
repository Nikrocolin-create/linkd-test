BEGIN;

ALTER TABLE public.links RENAME TO links_old;

CREATE TABLE public.links (
	created_at timestamptz(6) NOT NULL,
	short_code varchar(16) NOT NULL,
	long_url varchar(255) NOT NULL,
	expires_at timestamptz(6) NOT NULL DEFAULT '2099-12-31 00:00:00+01'::timestamp with time zone,
	CONSTRAINT links_pkey_short_code PRIMARY KEY (short_code)
) PARTITION BY HASH (short_code);
DROP INDEX IF EXISTS idx_links_long_url;
CREATE INDEX idx_links_long_url ON public.links USING btree (long_url);

CREATE TABLE public.links_0 PARTITION OF public.links FOR VALUES WITH (modulus 4, remainder 0);
CREATE TABLE public.links_1 PARTITION OF public.links FOR VALUES WITH (modulus 4, remainder 1);
CREATE TABLE public.links_2 PARTITION OF public.links FOR VALUES WITH (modulus 4, remainder 2);
CREATE TABLE public.links_3 PARTITION OF public.links FOR VALUES WITH (modulus 4, remainder 3);

INSERT into public.links (created_at, short_code, long_url, expires_at)
OVERRIDING SYSTEM VALUE SELECT created_at, short_code, long_url, expires_at FROM links_old;

COMMIT;