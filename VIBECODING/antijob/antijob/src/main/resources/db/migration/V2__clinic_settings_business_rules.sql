ALTER TABLE clinic_settings
    ADD COLUMN buffer_minutes INT NOT NULL DEFAULT 10,
    ADD COLUMN break_start TIME NULL,
    ADD COLUMN break_end TIME NULL,
    ADD COLUMN cancel_before_hours INT NOT NULL DEFAULT 12,
    ADD COLUMN max_pending_appointments INT NOT NULL DEFAULT 3;

UPDATE clinic_settings
SET break_start = '12:00', break_end = '13:00'
WHERE clinic_name = 'Nha Khoa VibeCode';
