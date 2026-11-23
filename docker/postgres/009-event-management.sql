-- Etkinlik yönetimi: etkinlikler silinmek yerine pasife alınır (rezervasyon geçmişi korunur).
-- Mevcut tüm etkinlikler aktif kalır.
BEGIN;
ALTER TABLE events ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE;
CREATE INDEX idx_events_active_starts_at ON events(active, starts_at);
COMMIT;
