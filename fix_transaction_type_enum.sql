-- Fix transaction_type enum to include platform_commission
-- Run this script to update the database schema

USE food_tour_platform;

-- Check current enum values
SHOW COLUMNS FROM wallet_transactions LIKE 'transaction_type';

-- Add platform_commission to the enum values
ALTER TABLE wallet_transactions
MODIFY COLUMN transaction_type ENUM(
    'deposit',
    'withdrawal',
    'payment',
    'refund',
    'received_payment',
    'admin_adjustment',
    'platform_commission'
) NOT NULL;

-- Verify the change
SHOW COLUMNS FROM wallet_transactions LIKE 'transaction_type';

SELECT 'Migration completed successfully!' AS status;

