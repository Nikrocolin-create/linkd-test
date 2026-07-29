--drop constraint because of expires_at;
CREATE INDEX idx_links_long_url ON links (long_url);
CREATE INDEX idx_links_short_code ON links (short_code);