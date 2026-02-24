-- Migration: Thêm cột has_refund_request vào bảng orders
-- Date: 2026-02-22
-- Description: Thêm flag để đánh dấu order có review yêu cầu refund

-- Thêm cột mới
ALTER TABLE orders
ADD COLUMN has_refund_request BOOLEAN NOT NULL DEFAULT FALSE
COMMENT 'Flag đánh dấu order có review yêu cầu refund';

-- Cập nhật dữ liệu hiện có: Set flag = true cho các order có review yêu cầu refund
UPDATE orders o
SET has_refund_request = TRUE
WHERE EXISTS (
    SELECT 1 FROM reviews r
    WHERE r.order_id = o.id
    AND r.has_refund_request = TRUE
);

-- Kiểm tra kết quả
SELECT
    o.id,
    o.order_number,
    o.has_refund_request,
    COUNT(r.id) as refund_review_count
FROM orders o
LEFT JOIN reviews r ON r.order_id = o.id AND r.has_refund_request = TRUE
GROUP BY o.id
ORDER BY o.has_refund_request DESC;

