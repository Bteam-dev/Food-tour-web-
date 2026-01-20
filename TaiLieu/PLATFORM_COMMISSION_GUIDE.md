# HỆ THỐNG HOA HỒNG NỀN TẢNG (PLATFORM COMMISSION)

## Tổng quan
Admin (nền tảng) tự động nhận **12% hoa hồng** trên mỗi đơn hàng thành công.

## Cơ chế hoạt động

### 1. Khi User thanh toán đơn hàng bằng Wallet:

**Flow thanh toán:**
1. **User thanh toán:** Trừ 100% giá trị đơn hàng từ ví của user
2. **Tính hoa hồng:** Hệ thống tự động tính 12% từ tổng giá trị đơn hàng
3. **Seller nhận tiền:** Seller chỉ nhận **88%** giá trị đơn hàng (sau khi trừ hoa hồng)
4. **Admin nhận hoa hồng:** Admin tự động nhận **12%** vào ví

**Ví dụ cụ thể:**
- Tổng đơn hàng: **100,000 VND**
- User trả: **100,000 VND** (đầy đủ)
- Platform commission (12%): **12,000 VND** → Vào ví Admin
- Seller nhận: **88,000 VND** (100,000 - 12,000)

### 2. Công thức tính toán:

```
platform_commission_amount = total_amount × 12% ÷ 100
seller_received_amount = total_amount - platform_commission_amount
```

### 3. Thông tin lưu trong Order:

| Field | Giá trị | Mô tả |
|-------|---------|-------|
| `total_amount` | 100,000 | Tổng giá trị đơn hàng (user thanh toán) |
| `platform_commission_rate` | 12.00 | Tỷ lệ hoa hồng (%) |
| `platform_commission_amount` | 12,000 | Số tiền hoa hồng thực tế |
| `seller_received_amount` | 88,000 | Số tiền seller thực nhận |

### 4. Giao dịch Wallet được tạo:

#### a) Giao dịch của User (Buyer):
```json
{
  "transaction_type": "payment",
  "amount": 100000,
  "description": "Thanh toán đơn hàng #ORDER_NUMBER"
}
```

#### b) Giao dịch của Seller:
```json
{
  "transaction_type": "received_payment",
  "amount": 88000,
  "description": "Nhận tiền từ đơn hàng #ORDER_NUMBER (đã trừ hoa hồng 12%)"
}
```

#### c) Giao dịch của Admin (Hoa hồng):
```json
{
  "transaction_type": "platform_commission",
  "amount": 12000,
  "description": "Hoa hồng nền tảng 12% từ đơn hàng #ORDER_NUMBER (Shop: SHOP_NAME)"
}
```

## Response API

### GET /api/user/orders/{orderId}

Response sẽ bao gồm thông tin hoa hồng:

```json
{
  "success": true,
  "data": {
    "id": 1,
    "orderNumber": "abc-123",
    "subtotal": 85000,
    "deliveryFee": 15000,
    "taxAmount": 0,
    "totalAmount": 100000,
    "platformCommissionRate": 12.00,
    "platformCommissionAmount": 12000,
    "sellerReceivedAmount": 88000,
    "paymentStatus": "paid",
    ...
  }
}
```

## Database Schema

### Bảng `orders`:
```sql
platform_commission_rate DECIMAL(5, 2) DEFAULT 12.00    -- Tỷ lệ hoa hồng
platform_commission_amount DECIMAL(15, 2) DEFAULT 0.00  -- Số tiền hoa hồng
seller_received_amount DECIMAL(15, 2) DEFAULT 0.00      -- Seller thực nhận
```

### Bảng `wallet_transactions`:
```sql
transaction_type ENUM(..., 'platform_commission')  -- Loại giao dịch hoa hồng
```

## Migration

Chạy file SQL: `platform_commission_migration.sql`

```bash
mysql -u root -p food_tour_app < TaiLieu/platform_commission_migration.sql
```

## Lưu ý quan trọng

### 1. Xác định Admin account:
- Hệ thống tự động tìm user đầu tiên có role = ADMIN
- Hoa hồng sẽ vào ví của admin này
- **Lưu ý:** Đảm bảo có ít nhất 1 admin account trong hệ thống

### 2. Tỷ lệ hoa hồng:
- Mặc định: **12%** (cố định)
- Có thể điều chỉnh cho từng đơn hàng qua field `platform_commission_rate` nếu cần

### 3. Hoàn tiền (Refund):
- Khi hoàn tiền, user nhận lại **100%** số tiền đã trả
- Seller bị trừ **100%** (bao gồm cả phần hoa hồng)
- **Admin KHÔNG tự động hoàn lại hoa hồng** (đây là chi phí xử lý giao dịch)

### 4. COD (Cash On Delivery):
- Hoa hồng chỉ áp dụng cho thanh toán qua **app_wallet**
- COD không tính hoa hồng tự động (xử lý manual)

## Testing

### Test case 1: Tạo và thanh toán đơn hàng
```bash
# 1. Tạo đơn hàng
POST /api/user/orders
{
  "cartItemIds": [1, 2],
  "paymentMethod": "app_wallet",
  "deliveryAddress": {...}
}

# 2. Thanh toán
POST /api/user/orders/{orderId}/pay

# 3. Kiểm tra wallet của seller (nhận 88%)
GET /api/user/wallet

# 4. Kiểm tra wallet của admin (nhận 12%)
# Login as admin
GET /api/user/wallet
```

### Expected Results:
- User wallet: -100,000 VND
- Seller wallet: +88,000 VND
- Admin wallet: +12,000 VND
- Order.platform_commission_amount: 12,000 VND

## Business Logic Summary

| Role | Nhận/Trả | % | Số tiền (ví dụ 100k) |
|------|----------|---|---------------------|
| User (Buyer) | Trả | 100% | -100,000 VND |
| Seller | Nhận | 88% | +88,000 VND |
| Admin (Platform) | Nhận | 12% | +12,000 VND |

**Total:** 88% + 12% = 100% ✅

