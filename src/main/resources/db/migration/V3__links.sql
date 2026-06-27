ALTER TABLE links ADD CONSTRAINT long_url_unique UNIQUE(long_url);
ALTER TABLE links DROP COLUMN ttl;
ALTER TABLE links ADD COLUMN expire_at timestamptz(6) NOT NULL DEFAULT '2099-12-31';