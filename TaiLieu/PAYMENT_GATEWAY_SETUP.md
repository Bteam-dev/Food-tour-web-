# Hướng Dẫn Cấu Hình Payment Gateway Sandbox

## 📋 Tổng Quan

Document này hướng dẫn chi tiết cách lấy credentials (API Key, Secret Key) để tích hợp các payment gateway sandbox vào ứng dụng.

---

## 🧪 Test API Bằng Postman

### Chuẩn Bị
1. Tải và cài đặt [Postman](https://www.postman.com/downloads/)
2. Đảm bảo server đang chạy ở `http://localhost:8080`
3. Đăng nhập và lấy JWT token

### Lấy JWT Token

#### 1. Đăng Ký Tài Khoản (Nếu Chưa Có)
```http
POST http://localhost:8080/api/public/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "email": "testuser@example.com",
  "password": "password123",
  "fullName": "Test User",
  "phone": "0123456789"
}
```

**Response:**
```json
{
  "message": "User registered successfully",
  "userId": 1
}
```

#### 2. Đăng Nhập
```http
POST http://localhost:8080/api/public/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

**Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "testuser",
  "roles": ["ROLE_USER"]
}
```

**📌 Quan Trọng:** Copy token này để dùng cho các request tiếp theo!

---

### Test Wallet APIs

#### 1. Xem Số Dư Ví
```http
GET http://localhost:8080/api/user/wallet/balance
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

**Response:**
```json
{
  "userId": 1,
  "balance": 0.0,
  "currency": "VND"
}
```

---

### Test PayPal Payment

#### 1. Tạo Payment URL
```http
POST http://localhost:8080/api/user/wallet/deposit/paypal
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "amount": 100000,
  "description": "Nạp tiền vào ví"
}
```

**Response:**
```json
{
  "paymentUrl": "https://www.sandbox.paypal.com/checkoutnow?token=EC-XXXX...",
  "orderId": "WALLET_DEP_1234567890",
  "amount": 100000,
  "currency": "VND"
}
```

#### 2. Test Flow Đầy Đủ
1. Copy `paymentUrl` từ response
2. Mở URL trong browser
3. Đăng nhập PayPal sandbox (dùng tài khoản Personal test)
4. Approve payment
5. Bạn sẽ được redirect về: `http://localhost:8080/api/user/wallet/paypal/success?orderId=WALLET_DEP_1234567890&paymentId=PAYID-XXX&PayerID=XXXXX`
6. Kiểm tra lại balance:
```http
GET http://localhost:8080/api/user/wallet/balance
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

**Expected Response:**
```json
{
  "userId": 1,
  "balance": 100000.0,
  "currency": "VND"
}
```

---

### Test VNPay Payment

#### 1. Tạo Payment URL
```http
POST http://localhost:8080/api/user/wallet/deposit/vnpay
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "amount": 200000,
  "description": "Nạp tiền qua VNPay"
}
```

**Response:**
```json
{
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=20000000&vnp_Command=pay&...",
  "orderId": "WALLET_DEP_1234567891",
  "amount": 200000,
  "currency": "VND"
}
```

#### 2. Test Flow
1. Copy `paymentUrl` và mở trong browser
2. Nhập thông tin thẻ test:
   - Số thẻ: `9704198526191432198`
   - Tên chủ thẻ: `NGUYEN VAN A`
   - Ngày phát hành: `07/15`
   - Mã OTP: `123456`
3. Click "Thanh toán"
4. Redirect về callback URL
5. Kiểm tra balance đã tăng

---

### Test MoMo Payment

#### 1. Tạo Payment URL
```http
POST http://localhost:8080/api/user/wallet/deposit/momo
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "amount": 300000,
  "description": "Nạp tiền qua MoMo"
}
```

**Response:**
```json
{
  "paymentUrl": "https://test-payment.momo.vn/gw_payment/payment/qr?partnerCode=...",
  "orderId": "WALLET_DEP_1234567892",
  "amount": 300000,
  "currency": "VND",
  "deeplink": "momo://app?action=payWithAppToken&..."
}
```

#### 2. Test Flow
**Cách 1: QR Code (Desktop)**
1. Copy `paymentUrl` và mở trong browser
2. Mở MoMo Sandbox App trên điện thoại
3. Quét QR code
4. Xác nhận thanh toán

**Cách 2: Deeplink (Mobile)**
1. Copy `deeplink` và mở trên điện thoại có MoMo App
2. App sẽ tự động mở và hiển thị payment
3. Xác nhận thanh toán

#### 3. Giả Lập Webhook (Development)
Vì webhook cần public URL, bạn có thể test manually:
```http
POST http://localhost:8080/api/user/wallet/momo/webhook
Content-Type: application/json

{
  "partnerCode": "MOMOIQA420180417",
  "orderId": "WALLET_DEP_1234567892",
  "requestId": "test-request-id",
  "amount": "300000",
  "orderInfo": "Nạp tiền qua MoMo",
  "orderType": "momo_wallet",
  "transId": "2147483647",
  "resultCode": "0",
  "message": "Successful.",
  "payType": "qr",
  "responseTime": "1698566400000",
  "extraData": "",
  "signature": "valid-signature-here"
}
```

**Note:** Signature cần tính toán đúng theo MoMo spec, hoặc tạm thời disable verification để test.

---

### Test ZaloPay Payment

#### 1. Tạo Payment URL
```http
POST http://localhost:8080/api/user/wallet/deposit/zalopay
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "amount": 400000,
  "description": "Nạp tiền qua ZaloPay"
}
```

**Response:**
```json
{
  "paymentUrl": "https://sb-openapi.zalopay.vn/v2/gateway/pay?order_token=...",
  "orderId": "WALLET_DEP_1234567893",
  "amount": 400000,
  "currency": "VND",
  "zpTransToken": "token-xxx-xxx"
}
```

#### 2. Test Flow
1. Copy `paymentUrl` và mở trong browser hoặc app
2. Đăng nhập ZaloPay (sandbox account)
3. Xác nhận thanh toán
4. ZaloPay sẽ gọi callback URL

---

### Test Lịch Sử Giao Dịch

#### 1. Xem Tất Cả Giao Dịch
```http
GET http://localhost:8080/api/user/wallet/transactions
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

**Response:**
```json
{
  "transactions": [
    {
      "id": 1,
      "transactionType": "deposit",
      "amount": 100000,
      "balanceBefore": 0,
      "balanceAfter": 100000,
      "status": "SUCCESS",
      "description": "Nạp tiền vào ví",
      "externalOrderId": "WALLET_DEP_1234567890",
      "createdAt": "2025-10-29T10:30:00"
    },
    {
      "id": 2,
      "transactionType": "deposit",
      "amount": 200000,
      "balanceBefore": 100000,
      "balanceAfter": 300000,
      "status": "SUCCESS",
      "description": "Nạp tiền qua VNPay",
      "externalOrderId": "WALLET_DEP_1234567891",
      "createdAt": "2025-10-29T11:00:00"
    }
  ],
  "totalTransactions": 2
}
```

#### 2. Xem Giao Dịch Theo Loại
```http
GET http://localhost:8080/api/user/wallet/transactions?type=deposit
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

#### 3. Xem Giao Dịch Theo Khoảng Thời Gian
```http
GET http://localhost:8080/api/user/wallet/transactions?startDate=2025-10-01&endDate=2025-10-31
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

---

### Test Thanh Toán Đơn Hàng Bằng Ví

#### 1. Tạo Đơn Hàng (Chưa Thanh Toán)
```http
POST http://localhost:8080/api/user/orders
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "deliveryAddressId": 1,
  "paymentMethod": "app_wallet",
  "cartItemIds": [1, 2, 3],
  "note": "Giao hàng giờ hành chính"
}
```

**Response:**
```json
{
  "orderId": 123,
  "totalAmount": 150000,
  "paymentStatus": "pending",
  "orderStatus": "pending"
}
```

#### 2. Thanh Toán Đơn Hàng
```http
POST http://localhost:8080/api/user/orders/123/pay
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "paymentMethod": "app_wallet"
}
```

**Response Success:**
```json
{
  "message": "Payment successful",
  "orderId": 123,
  "paymentStatus": "paid",
  "newBalance": 150000,
  "transactionId": 5
}
```

**Response Error (Không Đủ Tiền):**
```json
{
  "error": "Insufficient balance",
  "currentBalance": 100000,
  "requiredAmount": 150000,
  "shortfall": 50000
}
```

---

### Test Hoàn Tiền (Refund)

#### 1. Hủy Đơn Hàng Đã Thanh Toán
```http
POST http://localhost:8080/api/user/orders/123/cancel
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: application/json

{
  "reason": "Đổi ý không mua nữa"
}
```

**Response:**
```json
{
  "message": "Order cancelled and refunded",
  "orderId": 123,
  "refundAmount": 150000,
  "newBalance": 300000,
  "refundTransactionId": 6
}
```

#### 2. Kiểm Tra Giao Dịch Hoàn Tiền
```http
GET http://localhost:8080/api/user/wallet/transactions?type=refund
Authorization: Bearer YOUR_JWT_TOKEN_HERE
```

---

## 📦 Postman Collection

### Tạo Collection Nhanh

Tạo file `FoodTourApp_Payment.postman_collection.json`:

```json
{
  "info": {
    "name": "FoodTourApp Payment APIs",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8080"
    },
    {
      "key": "token",
      "value": ""
    }
  ],
  "item": [
    {
      "name": "Auth",
      "item": [
        {
          "name": "Register",
          "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"username\": \"testuser\",\n  \"email\": \"testuser@example.com\",\n  \"password\": \"password123\",\n  \"fullName\": \"Test User\",\n  \"phone\": \"0123456789\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/public/auth/register",
              "host": ["{{baseUrl}}"],
              "path": ["api", "public", "auth", "register"]
            }
          }
        },
        {
          "name": "Login",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "var jsonData = pm.response.json();",
                  "pm.collectionVariables.set(\"token\", jsonData.token);"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [{"key": "Content-Type", "value": "application/json"}],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"username\": \"testuser\",\n  \"password\": \"password123\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/public/auth/login",
              "host": ["{{baseUrl}}"],
              "path": ["api", "public", "auth", "login"]
            }
          }
        }
      ]
    },
    {
      "name": "Wallet",
      "item": [
        {
          "name": "Get Balance",
          "request": {
            "method": "GET",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"}
            ],
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/balance",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "balance"]
            }
          }
        },
        {
          "name": "Deposit via PayPal",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"amount\": 100000,\n  \"description\": \"Nạp tiền vào ví\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/deposit/paypal",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "deposit", "paypal"]
            }
          }
        },
        {
          "name": "Deposit via VNPay",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"amount\": 200000,\n  \"description\": \"Nạp tiền qua VNPay\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/deposit/vnpay",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "deposit", "vnpay"]
            }
          }
        },
        {
          "name": "Deposit via MoMo",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"amount\": 300000,\n  \"description\": \"Nạp tiền qua MoMo\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/deposit/momo",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "deposit", "momo"]
            }
          }
        },
        {
          "name": "Deposit via ZaloPay",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"amount\": 400000,\n  \"description\": \"Nạp tiền qua ZaloPay\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/deposit/zalopay",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "deposit", "zalopay"]
            }
          }
        },
        {
          "name": "Get Transactions",
          "request": {
            "method": "GET",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"}
            ],
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/transactions",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "transactions"]
            }
          }
        }
      ]
    },
    {
      "name": "Orders",
      "item": [
        {
          "name": "Create Order",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"deliveryAddressId\": 1,\n  \"paymentMethod\": \"app_wallet\",\n  \"cartItemIds\": [1, 2, 3],\n  \"note\": \"Giao hàng giờ hành chính\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/orders",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "orders"]
            }
          }
        },
        {
          "name": "Pay Order",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"paymentMethod\": \"app_wallet\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/orders/{{orderId}}/pay",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "orders", "{{orderId}}", "pay"]
            }
          }
        },
        {
          "name": "Cancel Order",
          "request": {
            "method": "POST",
            "header": [
              {"key": "Authorization", "value": "Bearer {{token}}"},
              {"key": "Content-Type", "value": "application/json"}
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"reason\": \"Đổi ý không mua nữa\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/orders/{{orderId}}/cancel",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "orders", "{{orderId}}", "cancel"]
            }
          }
        }
      ]
    }
  ]
}
```

### Import vào Postman
1. Mở Postman
2. Click **Import** → **File**
3. Chọn file `FoodTourApp_Payment.postman_collection.json`
4. Collection sẽ xuất hiện trong workspace

### Sử Dụng Collection
1. Chạy **Login** request → Token tự động được lưu vào biến `{{token}}`
2. Các request khác tự động dùng token này
3. Thay đổi `{{orderId}}` khi test thanh toán đơn hàng

---

## 🔍 Debug Tips

### 1. Check Logs
Mở terminal chạy Spring Boot, xem logs để debug:
```
2025-10-29 10:30:15 INFO  PayPal payment created: PAYID-XXXXX
2025-10-29 10:30:16 INFO  VNPay payment URL created for order: WALLET_DEP_123
2025-10-29 10:30:17 ERROR Failed to create MoMo payment: Connection timeout
```

### 2. Enable Debug Level
Thêm vào `application.properties`:
```properties
logging.level.com.example.FoodTourApp.service.impl=DEBUG
```

### 3. Test Signature Calculation
Tạo test request để verify signature:
```http
POST http://localhost:8080/api/debug/verify-signature
Content-Type: application/json

{
  "gateway": "momo",
  "data": "accessKey=xxx&amount=100000&...",
  "signature": "calculated-signature-here"
}
```

### 4. Mock Callback (Development Only)
Tắt signature verification tạm thời để test callback:
```java
// MoMoPaymentService.java
public boolean verifyCallback(Map<String, String> params) {
    // TODO: Remove this in production!
    if ("development".equals(environment)) {
        return "0".equals(params.get("resultCode"));
    }
    // ... normal verification
}
```

---

## 1. 💳 PayPal Sandbox

### Bước 1: Tạo PayPal Developer Account
1. Truy cập: https://developer.paypal.com
2. Click **Log In** hoặc **Sign Up** (nếu chưa có tài khoản)
3. Đăng nhập bằng tài khoản PayPal cá nhân hoặc tạo mới

### Bước 2: Tạo Sandbox App
1. Sau khi đăng nhập, vào **Dashboard**
2. Click vào **Apps & Credentials**
3. Chọn tab **Sandbox** (quan trọng!)
4. Click **Create App**
5. Nhập tên app (ví dụ: "FoodTourApp")
6. Click **Create App**

### Bước 3: Lấy Credentials
Sau khi tạo app, bạn sẽ thấy:
- **Client ID**: Copy và paste vào `paypal.client.id`
- **Secret**: Click **Show** để hiển thị, sau đó copy vào `paypal.client.secret`

### Bước 4: Cấu hình trong application.properties
```properties
paypal.mode=sandbox
paypal.client.id=YOUR_SANDBOX_CLIENT_ID_HERE
paypal.client.secret=YOUR_SANDBOX_SECRET_HERE
paypal.returnUrl=http://localhost:8080/api/user/wallet/paypal/success
paypal.cancelUrl=http://localhost:8080/api/user/wallet/paypal/cancel
```

### Test với Sandbox Account
1. Vào **Sandbox** → **Accounts**
2. PayPal tự động tạo sẵn 2 tài khoản test:
   - **Personal** (Buyer): Dùng để test thanh toán
   - **Business** (Seller): Dùng để nhận tiền
3. Click vào **...** → **View/Edit Account** để xem email và password
4. Dùng thông tin này để login khi test thanh toán

### 📌 Lưu Ý
- Credentials sandbox **KHÔNG** hoạt động với production
- Không cần thẻ tín dụng thật để test
- Tài khoản sandbox có sẵn số dư ảo

---

## 2. 🇻🇳 VNPay Sandbox

### Bước 1: Đăng Ký Tài Khoản Demo
VNPay không có public sandbox, bạn có 2 cách:

#### Cách 1: Sử Dụng Demo Credentials (Khuyến Nghị)
VNPay cung cấp credentials demo công khai để test:

```properties
vnpay.payUrl=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.returnUrl=http://localhost:8080/api/user/wallet/vnpay/callback
vnpay.tmnCode=DEMOSHOP
vnpay.hashSecret=DEMOSECRETKEY123456789012345678901234567890
vnpay.version=2.1.0
vnpay.command=pay
```

**Thông tin test:**
- Tên chủ thẻ: `NGUYEN VAN A`
- Số thẻ: `9704198526191432198`
- Ngày phát hành: `07/15`
- Mã OTP: `123456`

#### Cách 2: Đăng Ký Merchant Sandbox (Chính Thức)
1. Truy cập: https://sandbox.vnpayment.vn/merchantv2/
2. Liên hệ VNPay qua email: merchant@vnpay.vn
3. Cung cấp thông tin doanh nghiệp (cần GPKD)
4. Chờ VNPay duyệt và cấp tài khoản

### Bước 2: Lấy Credentials
Sau khi được cấp tài khoản:
1. Đăng nhập vào Merchant Portal
2. Vào **Cấu hình** → **Thông tin doanh nghiệp**
3. Copy:
   - **Mã TMN** → `vnpay.tmnCode`
   - **Hash Secret** → `vnpay.hashSecret`

### 📌 Lưu Ý
- Demo credentials chỉ dùng để test UI/UX
- Để test thực tế cần đăng ký merchant sandbox
- VNPay yêu cầu HTTPS cho production

---

## 3. 🟣 MoMo Sandbox

### Bước 1: Đăng Ký MoMo Partner
1. Truy cập: https://business.momo.vn
2. Click **Đăng ký**
3. Chọn **Tích hợp thanh toán**
4. Điền thông tin doanh nghiệp

### Bước 2: Truy Cập Sandbox
1. Sau khi đăng ký, đăng nhập vào **MoMo Business Portal**
2. Vào **Tài liệu** → **Sandbox**
3. MoMo cung cấp credentials test công khai:

```properties
momo.endpoint=https://test-payment.momo.vn/v2/gateway/api/create
momo.returnUrl=http://localhost:8080/api/user/wallet/momo/callback
momo.notifyUrl=http://localhost:8080/api/user/wallet/momo/webhook
momo.partnerCode=MOMOIQA420180417
momo.accessKey=SvDmj2cOTYZmQQ3H
momo.secretKey=PPuDXq1KowPT1ftR8DvlQTHhC03aul17
```

### Bước 3: Test Thanh Toán
1. Tải app **MoMo Sandbox** (khác với app MoMo chính)
2. Đăng ký tài khoản test trong app
3. App tự động có số dư ảo
4. Dùng số điện thoại test để thanh toán

### Lấy Credentials Riêng (Nếu Cần)
1. Đăng nhập **MoMo Business Portal**
2. Vào **Cấu hình** → **API Credentials**
3. Chọn môi trường **Sandbox**
4. Copy:
   - **Partner Code** → `momo.partnerCode`
   - **Access Key** → `momo.accessKey`
   - **Secret Key** → `momo.secretKey`

### 📌 Lưu Ý
- Credentials công khai đã đủ để test
- App MoMo Sandbox khác với app MoMo chính thức
- Cần webhook URL có thể access từ internet (dùng ngrok cho local)

---

## 4. 💙 ZaloPay Sandbox

### Bước 1: Đăng Ký ZaloPay Merchant
1. Truy cập: https://docs.zalopay.vn/
2. Click **Đăng ký tài khoản**
3. Hoặc liên hệ: merchant@zalopay.vn

### Bước 2: Sử Dụng Demo Credentials
ZaloPay cung cấp credentials demo công khai:

```properties
zalopay.endpoint=https://sb-openapi.zalopay.vn/v2/create
zalopay.callbackUrl=http://localhost:8080/api/user/wallet/zalopay/callback
zalopay.appId=2553
zalopay.key1=PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL
zalopay.key2=kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz
```

### Bước 3: Lấy Credentials Riêng (Sau Khi Được Duyệt)
1. Đăng nhập vào **ZaloPay Merchant Portal**
2. Vào **Cài đặt** → **Thông tin ứng dụng**
3. Chọn môi trường **Sandbox**
4. Copy:
   - **App ID** → `zalopay.appId`
   - **Key 1** (dùng để tạo MAC request) → `zalopay.key1`
   - **Key 2** (dùng để verify callback) → `zalopay.key2`

### Test Thanh Toán
1. Tải app **ZaloPay**
2. Đăng ký tài khoản test
3. Nạp tiền ảo vào ví test (ZaloPay có chức năng test payment)
4. Quét QR hoặc dùng deeplink để thanh toán

### 📌 Lưu Ý
- Demo credentials hoạt động ngay, không cần đăng ký
- Để có credentials riêng cần duyệt merchant (1-2 tuần)
- ZaloPay có tài liệu API khá đầy đủ tại docs.zalopay.vn

---

## 🔐 Bảo Mật Credentials

### ⚠️ QUAN TRỌNG
**KHÔNG BAO GIỜ** commit credentials vào Git công khai!

### Sử Dụng Environment Variables (Khuyến Nghị)
Thay vì hard-code trong `application.properties`, dùng biến môi trường:

```properties
# application.properties
paypal.client.id=${PAYPAL_CLIENT_ID}
paypal.client.secret=${PAYPAL_CLIENT_SECRET}
vnpay.tmnCode=${VNPAY_TMN_CODE}
vnpay.hashSecret=${VNPAY_HASH_SECRET}
# ... tương tự cho các gateway khác
```

### Tạo file application-local.properties
```properties
# application-local.properties (thêm vào .gitignore)
paypal.client.id=AeGYXQSalIT0fXuVT5Tm...
paypal.client.secret=EGYXQSalIT0fXuVT5Tm...
# ... các credentials thực tế
```

Sau đó run app với profile:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Hoặc Dùng .env File (Với Spring Boot 3.1+)
Tạo file `.env` ở root project:
```env
PAYPAL_CLIENT_ID=AeGYXQSalIT0fXuVT5Tm...
PAYPAL_CLIENT_SECRET=EGYXQSalIT0fXuVT5Tm...
VNPAY_TMN_CODE=DEMOSHOP
VNPAY_HASH_SECRET=DEMOSECRETKEY...
```

---

## 🧪 Testing Checklist

### Trước Khi Test
- [ ] Đã lấy đúng credentials sandbox (không phải production)
- [ ] Đã cấu hình đúng return URL và callback URL
- [ ] Server đang chạy ở đúng port (8080)
- [ ] Database đã được khởi tạo

### Test PayPal
- [ ] Click nút "Nạp tiền qua PayPal"
- [ ] Redirect đến PayPal sandbox login
- [ ] Login bằng tài khoản sandbox Personal
- [ ] Approve payment
- [ ] Redirect về app và balance tăng

### Test VNPay
- [ ] Click nút "Nạp tiền qua VNPay"
- [ ] Redirect đến VNPay payment page
- [ ] Nhập thông tin thẻ demo
- [ ] Nhập OTP: 123456
- [ ] Redirect về app và balance tăng

### Test MoMo
- [ ] Click nút "Nạp tiền qua MoMo"
- [ ] Hiển thị QR code hoặc deeplink
- [ ] Mở MoMo app (sandbox) và quét QR
- [ ] Xác nhận thanh toán
- [ ] Webhook được gọi và balance tăng

### Test ZaloPay
- [ ] Click nút "Nạp tiền qua ZaloPay"
- [ ] Redirect hoặc hiển thị QR
- [ ] Mở ZaloPay app và quét QR
- [ ] Xác nhận thanh toán
- [ ] Callback được gọi và balance tăng

---

## 🐛 Troubleshooting

### Lỗi "Invalid Client ID/Secret" (PayPal)
- ✅ Kiểm tra đang dùng **Sandbox** credentials (không phải Live)
- ✅ Copy lại Client ID và Secret (không có khoảng trắng thừa)
- ✅ Kiểm tra mode=sandbox trong config

### Lỗi "Invalid Signature" (VNPay/MoMo/ZaloPay)
- ✅ Kiểm tra hash secret/key chính xác
- ✅ Kiểm tra format signature string (thứ tự parameters)
- ✅ Kiểm tra encoding UTF-8
- ✅ Xem log để debug signature string

### Callback Không Được Gọi
- ✅ Đảm bảo callback URL accessible từ internet
- ✅ Dùng **ngrok** cho local development:
  ```bash
  ngrok http 8080
  ```
  Sau đó update callback URL thành: `https://abc123.ngrok.io/api/user/wallet/momo/callback`

### Payment URL Trả Về Null
- ✅ Check log để xem response từ payment gateway
- ✅ Kiểm tra endpoint URL đúng chưa
- ✅ Kiểm tra request parameters đầy đủ chưa

---

## 📚 Tài Liệu Tham Khảo

### PayPal
- Developer Portal: https://developer.paypal.com
- API Reference: https://developer.paypal.com/api/rest/
- Sandbox Guide: https://developer.paypal.com/tools/sandbox/

### VNPay
- Merchant Portal: https://sandbox.vnpayment.vn/merchantv2/
- Tài liệu API: Liên hệ merchant@vnpay.vn
- Demo Credentials: Có sẵn trong tài liệu integration

### MoMo
- Business Portal: https://business.momo.vn
- API Documentation: https://developers.momo.vn
- Sandbox Guide: https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions

### ZaloPay
- Developer Portal: https://docs.zalopay.vn
- API Reference: https://docs.zalopay.vn/v2/
- Sandbox: https://sb-openapi.zalopay.vn

---

## 💡 Tips & Best Practices

1. **Luôn test trên Sandbox trước** - Không bao giờ test thẳng trên production
2. **Log đầy đủ** - Enable logging để debug dễ dàng
3. **Handle errors gracefully** - Payment có thể fail vì nhiều lý do
4. **Implement retry mechanism** - Cho các trường hợp callback bị mất
5. **Verify signature** - Luôn verify signature trong callback để tránh fraud
6. **Test edge cases**:
   - User cancel payment
   - Network timeout
   - Invalid amount (âm, 0, quá lớn)
   - Duplicate transactions
7. **Monitor transactions** - Log tất cả transactions để audit

---

## 📞 Hỗ Trợ

Nếu gặp vấn đề khi tích hợp:

- **PayPal**: https://developer.paypal.com/support/
- **VNPay**: merchant@vnpay.vn hoặc hotline: 1900 55 55 77
- **MoMo**: merchantsupport@momo.vn hoặc hotline: 1900 54 54 41
- **ZaloPay**: merchant@zalopay.vn hoặc hotline: 1900 5555 77

---

**Cập nhật lần cuối**: 29/10/2025
**Version**: 1.0.0
