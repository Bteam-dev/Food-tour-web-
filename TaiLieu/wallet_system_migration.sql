-- Migration SQL cho hệ thống ví tiền trong app
-- Thực hiện theo thứ tự từ trên xuống dưới

-- 1. Thêm cột wallet_balance vào bảng users
ALTER TABLE users
ADD COLUMN wallet_balance DOUBLE NOT NULL DEFAULT 0.0 COMMENT 'Số dư ví của user';

-- 2. Tạo bảng wallet_transactions để lưu lịch sử giao dịch
CREATE TABLE IF NOT EXISTS wallet_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL COMMENT 'ID của user thực hiện giao dịch',
    transaction_type ENUM('deposit', 'withdrawal', 'payment', 'refund', 'received_payment', 'admin_adjustment') NOT NULL COMMENT 'Loại giao dịch',
    amount DOUBLE NOT NULL COMMENT 'Số tiền giao dịch',
    balance_before DOUBLE NOT NULL COMMENT 'Số dư trước khi giao dịch',
    balance_after DOUBLE NOT NULL COMMENT 'Số dư sau khi giao dịch',
    order_id INT NULL COMMENT 'ID đơn hàng liên quan (nếu có)',
    description TEXT NULL COMMENT 'Mô tả giao dịch',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời gian tạo',

    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='Lịch sử giao dịch ví';

-- 3. Cập nhật enum payment_method trong bảng orders để thêm app_wallet và ship_cod
ALTER TABLE orders
MODIFY COLUMN payment_method ENUM('app_wallet', 'ship_cod', 'cash', 'card', 'e_wallet', 'bank_transfer') NOT NULL
COMMENT 'Phương thức thanh toán: app_wallet=Ví trong app, ship_cod=Thanh toán khi nhận hàng';

-- 4. Tạo index cho hiệu suất tốt hơn
CREATE INDEX idx_orders_payment_method ON orders(payment_method);
CREATE INDEX idx_orders_payment_status ON orders(payment_status);

-- 5. Comment cho rõ ràng
ALTER TABLE users
MODIFY COLUMN wallet_balance DOUBLE NOT NULL DEFAULT 0.0
COMMENT 'Số dư ví của user (VND)';

-- ROLLBACK (nếu cần quay lại):
-- DROP TABLE IF EXISTS wallet_transactions;
-- ALTER TABLE users DROP COLUMN wallet_balance;
-- ALTER TABLE orders MODIFY COLUMN payment_method ENUM('cash', 'card', 'e_wallet', 'bank_transfer') NOT NULL;

