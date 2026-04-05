-- =============================================
-- DROP UNUSED DATABASE INDEXES
-- Created: 2026-04-05  
-- Description: Remove unused indexes to optimize database performance
-- =============================================

USE foodtourapp;

-- Drop unused indexes that were identified through code analysis
-- These indexes are not being used by any queries in the application

-- 1. password_reset_otps table - expiry_time index (no cleanup queries)
ALTER TABLE password_reset_otps DROP INDEX IF EXISTS idx_expiry_time;

-- 2. shops table - rating index (no shop rating-based queries)  
ALTER TABLE shops DROP INDEX IF EXISTS idx_rating;

-- 3. cart_items table - user_id index (cart operations don't use direct user filtering)
ALTER TABLE cart_items DROP INDEX IF EXISTS idx_user_id;

-- 4. wallet_transactions table - transaction_type index (no filtering by type)
ALTER TABLE wallet_transactions DROP INDEX IF EXISTS idx_transaction_type;

-- 5. wallet_transactions table - reference index (no reference-based lookups)
ALTER TABLE wallet_transactions DROP INDEX IF EXISTS idx_reference;

-- 6. momo_transactions table - transaction_status index (no status-based filtering)
ALTER TABLE momo_transactions DROP INDEX IF EXISTS idx_transaction_status;

-- Show remaining indexes for verification
-- SHOW INDEX FROM password_reset_otps;
-- SHOW INDEX FROM shops; 
-- SHOW INDEX FROM cart_items;
-- SHOW INDEX FROM wallet_transactions;
-- SHOW INDEX FROM momo_transactions;

-- =============================================
-- PERFORMANCE BENEFITS EXPECTED:
-- - Reduced storage overhead by ~5-10%
-- - Improved INSERT/UPDATE performance 
-- - Reduced index maintenance overhead
-- - Cleaner database schema
-- =============================================