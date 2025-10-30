# Hướng Dẫn Nạp Tiền Vào Ví Qua MOMO

## 1. Tổng Quan
Hệ thống đã được tích hợp với MOMO UAT (môi trường test) để cho phép người dùng nạp tiền vào ví trong app.

## 2. Cấu Hình MOMO UAT

### Thông tin tài khoản test:
- **Partner Code**: MOMO
- **Access Key**: F8BBA842ECF85
- **Secret Key**: K951B6PE1waDMi640xX08PD3vg6EkVlz
- **Endpoint**: https://test-payment.momo.vn/v2/gateway/api/create

### Test Cards (dùng để thanh toán test):
- **Card Number**: 9704 0000 0000 0018
- **Card Holder**: NGUYEN VAN A
- **Expiry Date**: 03/07
- **OTP**: OTP sẽ được hiển thị trên màn hình test

## 3. API Endpoints

### 3.1. Tạo Link Thanh Toán MOMO (Recommended)
**Endpoint**: `POST /api/user/wallet/deposit/momo`

**Headers**:
```
Authorization: Bearer {token}
Content-Type: application/json
```

**Request Body**:
```json
{
  "amount": 50000,
  "description": "Nạp tiền vào ví",
  "returnUrl": "http://localhost:3000/wallet/success"  // Optional
}
```

**Response Success**:
```json
{
  "success": true,
  "message": "Tạo link thanh toán MOMO thành công",
  "data": {
    "payUrl": "https://test-payment.momo.vn/gw_payment/...",
    "orderId": "DEPOSIT_1_1234567890",
    "requestId": "uuid-string",
    "amount": 50000,
    "message": "Successful.",
    "resultCode": 0
  }
}
```

**Cách sử dụng**:
1. Client gọi API này để tạo link thanh toán
2. Client redirect user đến `payUrl` trong response
3. User thanh toán trên trang MOMO
4. Sau khi thanh toán, MOMO sẽ redirect về `returnUrl` (hoặc default callback URL)
5. Server tự động nhận callback từ MOMO và cộng tiền vào ví

### 3.2. Nạp Tiền Trực Tiếp (Chỉ dùng cho Testing)
**Endpoint**: `POST /api/user/wallet/deposit`

**Request Body**:
```json
{
  "amount": 50000,
  "description": "Test nạp tiền"
}
```

### 3.3. Callback từ MOMO (Public - Không cần auth)
**Endpoint**: `GET /api/public/momo/callback`

Đây là endpoint để MOMO redirect user về sau khi thanh toán. Server sẽ tự động xử lý.

**Query Parameters** (tự động gửi từ MOMO):
- `partnerCode`: MOMO
- `orderId`: Mã đơn hàng
- `requestId`: Request ID
- `amount`: Số tiền
- `resultCode`: 0 = success, khác 0 = failed
- `message`: Thông báo
- `transId`: Transaction ID từ MOMO
- `signature`: Chữ ký xác thực

### 3.4. IPN từ MOMO (Server-to-Server)
**Endpoint**: `POST /api/public/momo/ipn`

Đây là endpoint để MOMO gửi thông báo thanh toán đến server (server-to-server). Hệ thống sẽ tự động xử lý.

### 3.5. Xem Thông Tin Ví
**Endpoint**: `GET /api/user/wallet`

**Response**:
```json
{
  "success": true,
  "data": {
    "userId": 1,
    "fullName": "Nguyễn Văn A",
    "balance": 150000.00,
    "recentTransactions": [...]
  }
}
```

### 3.6. Xem Lịch Sử Giao Dịch
**Endpoint**: `GET /api/user/wallet/transactions`

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 1,
      "userFullName": "Nguyễn Văn A",
      "transactionType": "deposit",
      "amount": 50000.00,
      "balanceBefore": 100000.00,
      "balanceAfter": 150000.00,
      "description": "Nạp tiền qua MOMO - Nạp tiền vào ví (TransID: 123456)",
      "createdAt": "2025-10-30T10:30:00"
    }
  ]
}
```

## 4. Luồng Hoạt Động

### Flow 1: Nạp tiền thành công
```
1. User gọi API: POST /api/user/wallet/deposit/momo
   └─> Server tạo MomoTransaction (status=pending)
   └─> Server gọi MOMO API để tạo link thanh toán
   └─> Server trả về payUrl cho client

2. Client redirect user đến payUrl
   └─> User nhập thông tin thẻ test
   └─> User xác nhận OTP

3. MOMO redirect về callback URL
   └─> Server nhận callback với resultCode=0
   └─> Server cập nhật MomoTransaction (status=success)
   └─> Server cộng tiền vào wallet_balance của user
   └─> Server tạo WalletTransaction (type=deposit)

4. User thấy số dư đã tăng
```

### Flow 2: Nạp tiền thất bại
```
1-2. (Giống flow thành công)

3. MOMO redirect về callback URL
   └─> Server nhận callback với resultCode != 0
   └─> Server cập nhật MomoTransaction (status=failed)
   └─> KHÔNG cộng tiền vào ví

4. User thấy thông báo lỗi
```

## 5. Database Schema

### Table: momo_transactions
```sql
CREATE TABLE momo_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    order_id VARCHAR(255) NOT NULL UNIQUE,
    request_id VARCHAR(255) NOT NULL,
    amount DECIMAL(15, 2) NOT NULL,
    trans_id BIGINT,
    status ENUM('pending', 'success', 'failed', 'cancelled'),
    result_code INT,
    message TEXT,
    pay_url TEXT,
    description TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## 6. Test Trên MOMO UAT

### Bước 1: Tạo link thanh toán
```bash
curl -X POST http://localhost:8080/api/user/wallet/deposit/momo \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 50000,
    "description": "Test nạp tiền"
  }'
```

### Bước 2: Mở link payUrl trên browser
Copy `payUrl` từ response và mở trên browser

### Bước 3: Thanh toán với thẻ test
- Chọn "Thanh toán bằng thẻ ATM"
- Nhập thông tin thẻ test:
  - Card Number: 9704 0000 0000 0018
  - Card Holder: NGUYEN VAN A
  - Expiry Date: 03/07
- Nhập OTP hiển thị trên màn hình
- Xác nhận thanh toán

### Bước 4: Kiểm tra kết quả
```bash
curl -X GET http://localhost:8080/api/user/wallet \
  -H "Authorization: Bearer YOUR_TOKEN"
```

## 7. Lưu Ý Quan Trọng

### 7.1. Security
- ✅ Callback URL phải là public (không cần authentication)
- ✅ Nên verify signature từ MOMO để đảm bảo request hợp lệ
- ✅ Kiểm tra transaction đã được xử lý chưa để tránh double-spending

### 7.2. Production Deployment
Khi deploy lên production, cần thay đổi:
- `momo.endpoint`: Đổi sang production endpoint
- `momo.partner-code`, `access-key`, `secret-key`: Dùng credentials thật
- `momo.return-url`: Đổi sang domain thật (phải HTTPS)
- `momo.notify-url`: Đổi sang domain thật (phải HTTPS và accessible từ internet)

### 7.3. Callback URL Requirements
- Phải accessible từ internet (MOMO cần gọi được)
- Nếu test local, có thể dùng ngrok để expose localhost:
  ```bash
  ngrok http 8080
  ```
  Sau đó cập nhật `momo.notify-url` thành URL ngrok

### 7.4. Amount Validation
- Minimum deposit: 10,000 VND
- Maximum deposit: Tùy theo cấu hình MOMO

## 8. Troubleshooting

### Lỗi: "Không thể tạo link thanh toán MOMO"
- Kiểm tra credentials (partner-code, access-key, secret-key)
- Kiểm tra endpoint có đúng không
- Xem log để biết chi tiết lỗi từ MOMO

### Lỗi: "Invalid signature"
- Kiểm tra secret-key
- Kiểm tra format của rawSignature có đúng không

### Callback không được gọi
- Kiểm tra notify-url có accessible từ internet không
- Nếu test local, dùng ngrok
- Kiểm tra log MOMO có gọi IPN không

### Tiền không được cộng vào ví
- Kiểm tra log xem callback có được xử lý không
- Kiểm tra resultCode trong callback (phải = 0)
- Kiểm tra status của MomoTransaction trong database

## 9. Frontend Integration Example

```javascript
// React example
const handleDeposit = async () => {
  try {
    const response = await fetch('/api/user/wallet/deposit/momo', {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        amount: 50000,
        description: 'Nạp tiền vào ví',
        returnUrl: 'http://localhost:3000/wallet/success'
      })
    });
    
    const data = await response.json();
    
    if (data.success) {
      // Redirect user đến trang thanh toán MOMO
      window.location.href = data.data.payUrl;
    }
  } catch (error) {
    console.error('Error:', error);
  }
};
```

## 10. Monitoring & Logs

Server sẽ log các thông tin quan trọng:
- Khi tạo link thanh toán
- Khi nhận callback từ MOMO
- Khi cộng tiền vào ví
- Khi có lỗi xảy ra

Kiểm tra logs để debug:
```bash
tail -f logs/spring.log
```

