-- Migration: Thêm column variant_hash vào bảng cart_items
-- Chạy script này TRƯỚC khi restart backend

-- Bước 1: Thêm column variant_hash với default 'none' để không lỗi NOT NULL với record cũ
ALTER TABLE cart_items ADD COLUMN variant_hash VARCHAR(64) NOT NULL DEFAULT 'none';

-- Bước 2: Xóa unique constraint cũ (chỉ user_id + product_id)
ALTER TABLE cart_items DROP INDEX unique_cart_item;

-- Bước 3: Tạo unique constraint mới gồm cả variant_hash
ALTER TABLE cart_items ADD CONSTRAINT unique_cart_item UNIQUE (user_id, product_id, variant_hash);

