ALTER TABLE links DROP COLUMN expire_at;
ALTER TABLE links ADD COLUMN expires_at timestamptz(6) NOT NULL DEFAULT '2099-12-31';