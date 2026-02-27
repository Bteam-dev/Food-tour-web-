-- Migration: thêm các cột metadata cho file/media trong bảng messages
-- Chạy script này trên database hiện tại

ALTER TABLE messages
    ADD COLUMN IF NOT EXISTS file_name  VARCHAR(512)  NULL COMMENT 'Tên file gốc (dùng khi messageType != TEXT)',
    ADD COLUMN IF NOT EXISTS mime_type  VARCHAR(128)  NULL COMMENT 'MIME type: image/jpeg, video/mp4, audio/mpeg, ...',
    ADD COLUMN IF NOT EXISTS file_size  BIGINT        NULL COMMENT 'Kích thước file tính bằng bytes';

-- Cập nhật ENUM message_type để thêm VIDEO và AUDIO
-- (MySQL: cần ALTER COLUMN vì ENUM không hỗ trợ ADD IF NOT EXISTS)
ALTER TABLE messages
    MODIFY COLUMN message_type ENUM('TEXT','IMAGE','VIDEO','AUDIO','FILE') NOT NULL DEFAULT 'TEXT';

