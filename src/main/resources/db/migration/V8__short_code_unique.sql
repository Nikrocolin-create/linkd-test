-- V7 по ошибке повесил unique_links_short_code на long_url. Здесь исправляем.
-- Уникальность нужна именно на short_code: он возвращается пользователю и по нему идёт lookup.
-- Решение: код НЕ переиспользуется после истечения ссылки. Иначе старые QR-коды, закладки
-- и ссылки в переписке начали бы вести на чужой контент. Истёкшие строки остаются
-- надгробиями, живость проверяется по expires_at в запросе.
ALTER TABLE links DROP CONSTRAINT IF EXISTS unique_links_short_code;
ALTER TABLE links ADD CONSTRAINT links_short_code_key UNIQUE (short_code);
