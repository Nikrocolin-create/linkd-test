--drop constraint because of expires_at;
DROP INDEX IF EXISTS idx_links_short_code;
ALTER TABLE links ADD CONSTRAINT unique_links_short_code UNIQUE (long_url);
