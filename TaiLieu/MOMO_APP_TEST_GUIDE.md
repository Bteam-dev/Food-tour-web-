# Hướng Dẫn Test Nạp Tiền Bằng Tài Khoản MOMO UAT

## Tổng Quan
Bạn có thể test chức năng nạp tiền theo 2 cách:
1. **Thanh toán bằng App MOMO UAT trên điện thoại** (khuyên dùng) ← Đây là cách dùng tài khoản MOMO UAT bạn vừa tạo
2. **Thanh toán bằng thẻ test** (dùng số thẻ giả)

---

## Cách 1: Test Bằng App MOMO UAT (Tài khoản của bạn)

### Bước 1: Gọi API tạo link thanh toán
```bash
POST /api/user/wallet/deposit/momo
Authorization: Bearer {your_token}
Content-Type: application/json

{
  "amount": 50000,
  "description": "Nạp tiền vào ví",
  "paymentMethod": "app"
}
```

**Lưu ý**: `paymentMethod: "app"` là quan trọng để sử dụng App MOMO!

### Bước 2: Response trả về
```json
{
  "success": true,
  "message": "Tạo link thanh toán MOMO thành công",
  "data": {
    "payUrl": "https://test-payment.momo.vn/gw_payment/...",
    "orderId": "DEPOSIT_1_1234567890",
    "amount": 50000,
    "resultCode": 0
  }
}
```

### Bước 3: Mở link trên điện thoại
- Copy `payUrl` và gửi về điện thoại (qua Telegram, email, v.v.)
- Hoặc tạo QR code từ link này
- Mở link trên trình duyệt điện thoại

### Bước 4: Thanh toán trên App MOMO
- Trang thanh toán MOMO sẽ hiển thị
- Chọn **"Thanh toán bằng Ví MoMo"**
- App MOMO UAT sẽ tự động mở (nếu đã cài)
- Đăng nhập bằng tài khoản MOMO UAT của bạn
- Xác nhận thanh toán trong app
- Nhập mã PIN/vân tay để hoàn tất

### Bước 5: Kiểm tra kết quả
- MOMO sẽ redirect về callback URL
- Server tự động nhận IPN từ MOMO
- Tiền sẽ được cộng vào ví trong app của bạn

**Kiểm tra balance:**
```bash
GET /api/user/wallet
Authorization: Bearer {your_token}
```

---

## Cách 2: Test Bằng Thẻ Test (Không cần App MOMO)

### Bước 1: Gọi API với paymentMethod = "card"
```bash
POST /api/user/wallet/deposit/momo
Authorization: Bearer {your_token}
Content-Type: application/json

{
  "amount": 50000,
  "description": "Nạp tiền vào ví",
  "paymentMethod": "card"
}
```

### Bước 2: Mở payUrl và nhập thông tin thẻ test
- **Card Number**: 9704 0000 0000 0018
- **Card Holder**: NGUYEN VAN A
- **Expiry Date**: 03/07
- **OTP**: Sẽ hiển thị trên màn hình test (thường là `OTP` hoặc một số cố định)

### Bước 3: Xác nhận và kiểm tra kết quả

---

## So Sánh 2 Cách

| Tính năng | App MOMO UAT | Thẻ Test |
|-----------|--------------|----------|
| Cần tài khoản MOMO | ✅ Có (tài khoản UAT bạn tạo) | ❌ Không |
| Cần cài App | ✅ Có | ❌ Không |
| Giống thực tế | ✅ 100% | ⚠️ 80% |
| Dễ test | ⚠️ Cần điện thoại | ✅ Test ngay trên máy |
| Xem giao dịch trong App | ✅ Có | ❌ Không |

---

## Lưu Ý Quan Trọng

### 1. Callback URL phải public
Server của bạn phải accessible từ internet để MOMO gọi được IPN.

**Nếu test local**, dùng ngrok:
```bash
ngrok http 8080
```

Sau đó cập nhật `application.properties`:
```properties
momo.notify-url=https://abc123.ngrok.io/api/public/momo/ipn
momo.return-url=https://abc123.ngrok.io/api/public/momo/callback
```

### 2. Kiểm tra logs
```bash
tail -f logs/spring.log
```

Logs quan trọng:
- ✅ "Creating MOMO deposit payment..."
- ✅ "Calling MOMO API..."
- ✅ "MOMO response: ..."
- ✅ "Handling MOMO callback..."
- ✅ "Deposit successful for user..."

### 3. Kiểm tra database
```sql
-- Kiểm tra giao dịch MOMO
SELECT * FROM momo_transactions ORDER BY created_at DESC LIMIT 5;

-- Kiểm tra wallet transaction
SELECT * FROM wallet_transactions ORDER BY created_at DESC LIMIT 5;

-- Kiểm tra balance
SELECT id, email, wallet_balance FROM users WHERE id = YOUR_USER_ID;
```

---

## Troubleshooting

### Lỗi: "App MOMO không mở"
- Đảm bảo đã cài App MOMO UAT (không phải App MOMO chính thức)
- Thử mở link trên trình duyệt khác
- Hoặc chuyển sang dùng thẻ test (paymentMethod: "card")

### Lỗi: "Callback không được gọi"
- Kiểm tra `momo.notify-url` có public không
- Dùng ngrok nếu test local
- Kiểm tra firewall

### Lỗi: "Tiền không được cộng vào ví"
- Kiểm tra logs: có nhận callback không?
- Kiểm tra `resultCode` trong callback (phải = 0)
- Kiểm tra bảng `momo_transactions` xem status là gì

### Lỗi: "Invalid signature"
- Kiểm tra `secret-key` trong config
- Không cần verify signature khi test (có thể comment out)

---

## Ví Dụ Thực Tế

### Example Request (dùng App MOMO)
```json
{
  "amount": 100000,
  "description": "Nạp 100k vào ví",
  "paymentMethod": "app"
}
```

### Example Response
```json
{
  "success": true,
  "message": "Tạo link thanh toán MOMO thành công",
  "data": {
    "payUrl": "https://test-payment.momo.vn/gw_payment/transactionProcessor?partnerCode=MOMO&...",
    "orderId": "DEPOSIT_5_1698123456789",
    "requestId": "abc-123-def-456",
    "amount": 100000,
    "message": "Successful.",
    "resultCode": 0
  }
}
```

### Logs khi thành công
```
2025-10-30 10:00:00 INFO  Creating MOMO deposit payment for user 5, amount: 100000, paymentMethod: app
2025-10-30 10:00:00 INFO  Using requestType: payWithATM for paymentMethod: app
2025-10-30 10:00:01 INFO  Calling MOMO API: https://test-payment.momo.vn/v2/gateway/api/create
2025-10-30 10:00:02 INFO  MOMO response: {"resultCode":0,"message":"Successful.","payUrl":"..."}
2025-10-30 10:01:30 INFO  Handling MOMO callback for orderId: DEPOSIT_5_1698123456789
2025-10-30 10:01:30 INFO  Result code: 0, Message: Successful.
2025-10-30 10:01:31 INFO  Deposit successful for user 5. Balance: 50000.00 -> 150000.00
```

---

## Tóm Tắt Nhanh

1. ✅ Gọi API với `paymentMethod: "app"`
2. ✅ Mở `payUrl` trên điện thoại
3. ✅ Chọn "Thanh toán bằng Ví MoMo"
4. ✅ App MOMO UAT tự động mở
5. ✅ Đăng nhập tài khoản UAT của bạn
6. ✅ Xác nhận thanh toán
7. ✅ Kiểm tra balance đã tăng

**Xong! Đơn giản vậy thôi! 🎉**

