-- Migration: Remove is_default and address_type from addresses table
-- Date: 2026-02-06
-- Reason: These fields are not needed since addresses are created per order/shop, not saved as address book

-- Xóa column is_default
ALTER TABLE addresses DROP COLUMN IF EXISTS is_default;

-- Xóa column address_type
ALTER TABLE addresses DROP COLUMN IF EXISTS address_type;

-- Verify columns đã bị xóa
DESCRIBE addresses;

