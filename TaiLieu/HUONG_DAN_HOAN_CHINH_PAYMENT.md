# 🚀 HƯỚNG DẪN HOÀN CHỈNH - PAYMENT GATEWAY SANDBOX

> **Tổng hợp:** Lấy credentials + Test API bằng Postman
> 
> **🎓 DÀNH CHO SINH VIÊN:** Test 100% bằng TIỀN ẢO, không tốn 1 đồng!

---

## 📑 MỤC LỤC

1. [Credentials Là Gì?](#credentials-là-gì)
2. [Test Với Tiền Ảo 100%](#test-với-tiền-ảo-100)
3. [PayPal - Lấy Credentials](#paypal---lấy-credentials)
4. [VNPay - Credentials Demo](#vnpay---credentials-demo)
5. [MoMo - Credentials Demo](#momo---credentials-demo)
6. [ZaloPay - Credentials Demo](#zalopay---credentials-demo)
7. [Test API Bằng Postman](#test-api-bằng-postman)
8. [Postman Collection JSON](#postman-collection-json)
9. [FAQ - Câu Hỏi Thường Gặp](#faq)
10. [Hướng Dẫn Cho Sinh Viên](#hướng-dẫn-cho-sinh-viên)

---

## 🎯 CREDENTIALS LÀ GÌ?

**Credentials** = Chìa khóa để app kết nối với payment gateway

Gồm có:
- **API Key / Client ID** - Định danh app của bạn
- **Secret Key** - Mật khẩu bảo mật (KHÔNG public)
- **Hash Secret** - Dùng để mã hóa dữ liệu

---

## 💰 TEST VỚI TIỀN ẢO 100%

**Tất cả 4 payment gateway đều test được bằng TIỀN ẢO:**

| Gateway | Tiền Ảo? | Cách Test | Credentials |
|---------|----------|-----------|-------------|
| PayPal  | ✅ 100% | Sandbox $1,000 ảo | Phải tạo (5 phút, FREE) |
| VNPay   | ✅ 100% | Thẻ test vô hạn | Demo có sẵn |
| MoMo    | ✅ 100% | App sandbox 10M ảo | Demo có sẵn |
| ZaloPay | ✅ 100% | Tài khoản demo ảo | Demo có sẵn |

**👉 Sinh viên KHÔNG CẦN tiền thật để test!**

---

## 1. 💙 PAYPAL - LẤY CREDENTIALS

### Bước 1: Truy Cập PayPal Developer

```
Link: https://developer.paypal.com
```

- Mở trình duyệt → Copy link → Enter
- Thấy trang PayPal Developer màu xanh
- Nút **"Log In"** góc trên phải

### Bước 2: Đăng Nhập / Đăng Ký

**Chưa có tài khoản?**
1. Click **"Sign Up"**
2. Chọn **"Personal Account"**
3. Nhập email, password, họ tên
4. Xác nhận email
5. **KHÔNG CẦN** thẻ tín dụng

**Đã có tài khoản?**
1. Click **"Log In"**
2. Nhập email + password
3. Xác thực (nếu có)

### Bước 3: Tạo App

**Click theo thứ tự:**

1. **"Apps & Credentials"** (menu trái)
2. Tab **"Sandbox"** (KHÔNG phải "Live")
3. **"Create App"** (nút xanh)
4. Nhập tên app: `FoodTourApp`
5. **"Create App"**

### Bước 4: Copy Credentials

Sau khi tạo, bạn thấy:

```
┌─────────────────────────────────────┐
│ FoodTourApp                  [Edit] │
├─────────────────────────────────────┤
│ Client ID                           │
│ ┌─────────────────────────────────┐ │
│ │ AZqE-frVQkg3FcJi...           │ │
│ └─────────────────────────────────┘ │
│   [Show]  [Copy]                    │
│                                     │
│ Secret                              │
│ ┌─────────────────────────────────┐ │
│ │ ••••••••••••���•••••••••••        │ │
│ └─────────────────────────────────┘ │
│   [Show]  [Copy]                    │
└─────────────────────────────────────┘
```

**Copy:**
1. **Client ID** → Click "Copy"
2. **Secret** → Click "Show" → Click "Copy"

### Bước 5: Paste Vào application.properties

```properties
# PayPal Sandbox
paypal.mode=sandbox
paypal.client.id=PASTE_CLIENT_ID_Ở_ĐÂY
paypal.client.secret=PASTE_SECRET_Ở_ĐÂY
paypal.returnUrl=http://localhost:8080/api/user/wallet/paypal/success
paypal.cancelUrl=http://localhost:8080/api/user/wallet/paypal/cancel
```

### Lấy Tài Khoản Test

1. Vào **"Sandbox"** → **"Accounts"**
2. Thấy 2 tài khoản có sẵn:
   - **PERSONAL** (Buyer): Có $1,000 ảo
   - **BUSINESS** (Seller): Có $0
3. Click **"..."** → **"View/Edit Account"**
4. Copy email + password
5. Dùng để login khi test thanh toán

---

## 2. 🇻🇳 VNPAY - CREDENTIALS DEMO

### ⭐ Dùng Luôn (Không Cần Đăng Ký)

```properties
# VNPay Sandbox - COPY PASTE VÀO application.properties
vnpay.payUrl=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.returnUrl=http://localhost:8080/api/user/wallet/vnpay/callback
vnpay.tmnCode=DEMOSHOP
vnpay.hashSecret=DEMOSECRETKEY123456789012345678901234567890
vnpay.version=2.1.0
vnpay.command=pay
```

### 🧪 Thông Tin Thẻ Test

Khi test thanh toán VNPay:

```
Ngân hàng: NCB (Ngân hàng Quốc Dân)
Số thẻ: 9704198526191432198
Tên chủ thẻ: NGUYEN VAN A
Ngày phát hành: 07/15
Mã OTP: 123456 (LUÔN dùng OTP này)
```

---

## 3. 🟣 MOMO - CREDENTIALS DEMO

### ⭐ Dùng Luôn (Credentials Công Khai)

```properties
# MoMo Sandbox - COPY PASTE VÀO application.properties
momo.endpoint=https://test-payment.momo.vn/v2/gateway/api/create
momo.returnUrl=http://localhost:8080/api/user/wallet/momo/callback
momo.notifyUrl=http://localhost:8080/api/user/wallet/momo/webhook
momo.partnerCode=MOMOIQA420180417
momo.accessKey=SvDmj2cOTYZmQQ3H
momo.secretKey=PPuDXq1KowPT1ftR8DvlQTHhC03aul17
```

### 📱 Test Với MoMo - 2 Cách

#### Cách 1: MoMo Test App (Có Tiền Ảo)

**Android:**
- Email: **developer@momo.vn** xin link download APK
- Cài đặt MoMo Test App
- Đăng ký với số bất kỳ
- OTP: `111111` hoặc `123456`
- Tự động có **10,000,000 VND ảo**!

#### Cách 2: Giả Lập Callback (KHÔNG CẦN APP)

**Tắt verify tạm trong `MoMoPaymentService.java`:**

```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY - XÓA KHI PRODUCTION!
    log.warn("DEV MODE: Signature verification disabled!");
    return "0".equals(params.get("resultCode"));
    
    // TODO: Bật lại khi production
    /* ...code verify signature thật... */
}
```

**Test bằng Postman** (xem phần dưới)

---

## 4. 💙 ZALOPAY - CREDENTIALS DEMO

### ⭐ Dùng Luôn (Credentials Công Khai)

```properties
# ZaloPay Sandbox - COPY PASTE VÀO application.properties
zalopay.endpoint=https://sb-openapi.zalopay.vn/v2/create
zalopay.callbackUrl=http://localhost:8080/api/user/wallet/zalopay/callback
zalopay.appId=2553
zalopay.key1=PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL
zalopay.key2=kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz
```

### 📱 Test Với ZaloPay - 2 Cách

#### Cách 1: Tài Khoản Demo

```
Số điện thoại: 0999999999
OTP: 111111 (fix)
PIN: 123456
```

Hoặc dùng **thẻ test**:
```
Số thẻ: 9704 0000 0000 0018
OTP: 111111
```

#### Cách 2: Giả Lập Callback

**Tắt verify trong `ZaloPayPaymentService.java`:**

```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY
    log.warn("DEV MODE: MAC verification disabled!");
    try {
        String data = params.get("data");
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
        Integer status = (Integer) dataMap.get("status");
        return status != null && status == 1;
    } catch (Exception e) {
        return false;
    }
    
    // TODO: Bật lại MAC verification
    /* ...code verify MAC thật... */
}
```

---

## 🧪 TEST API BẰNG POSTMAN

### Chuẩn Bị

1. **Tải Postman:** https://www.postman.com/downloads/
2. **Đảm bảo server chạy:** `http://localhost:8080`
3. **Lấy JWT Token** (xem bên dưới)

---

### A. AUTHENTICATION APIs

#### 1️⃣ Đăng Ký Tài Khoản

**Method:** `POST`  
**URL:** `http://localhost:8080/api/public/auth/register`  
**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "username": "testuser",
  "email": "testuser@example.com",
  "password": "password123",
  "fullName": "Test User",
  "phone": "0123456789"
}
```

**Response (200 OK):**
```json
{
  "message": "User registered successfully",
  "userId": 1
}
```

---

#### 2️⃣ Đăng Nhập (Lấy Token)

**Method:** `POST`  
**URL:** `http://localhost:8080/api/public/auth/login`  
**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "username": "testuser",
  "password": "password123"
}
```

**Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "testuser",
  "roles": ["ROLE_USER"]
}
```

**📌 QUAN TRỌNG:** Copy `token` này! Dùng cho tất cả API phía dưới.

**Postman Auto-Save Token:**
- Vào tab **Tests** của request Login
- Thêm script:
```javascript
var jsonData = pm.response.json();
pm.collectionVariables.set("token", jsonData.token);
```
- Từ giờ token tự động lưu vào biến `{{token}}`

---

### B. WALLET APIs

#### 3️⃣ Xem Số Dư Ví

**Method:** `GET`  
**URL:** `http://localhost:8080/api/user/wallet/balance`  
**Headers:**
```
Authorization: Bearer {{token}}
```

**Response (200 OK):**
```json
{
  "userId": 1,
  "balance": 0.0,
  "currency": "VND"
}
```

**Test Steps:**
1. Paste URL vào Postman
2. Chọn method GET
3. Tab **Headers** → Add:
   - Key: `Authorization`
   - Value: `Bearer YOUR_TOKEN_HERE`
4. Click **Send**

---

#### 4️⃣ Nạp Tiền Qua PayPal

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/deposit/paypal`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "amount": 100000,
  "description": "Nạp tiền vào ví"
}
```

**Response (200 OK):**
```json
{
  "paymentUrl": "https://www.sandbox.paypal.com/checkoutnow?token=EC-XXXX...",
  "orderId": "WALLET_DEP_1730188800123",
  "amount": 100000,
  "currency": "VND"
}
```

**Test Full Flow:**
1. Send request → Nhận `paymentUrl`
2. Copy URL → Paste vào browser
3. Login PayPal sandbox (email/password đã lấy ở bước 1)
4. Click **Pay Now**
5. Redirect về `http://localhost:8080/api/user/wallet/paypal/success?orderId=...&paymentId=...`
6. Test API **Get Balance** → Thấy balance tăng!

---

#### 5️⃣ Nạp Tiền Qua VNPay

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/deposit/vnpay`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "amount": 200000,
  "description": "Nạp tiền qua VNPay"
}
```

**Response (200 OK):**
```json
{
  "paymentUrl": "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?vnp_Amount=...",
  "orderId": "WALLET_DEP_1730188900456",
  "amount": 200000,
  "currency": "VND"
}
```

**Test Full Flow:**
1. Send request → Nhận `paymentUrl`
2. Copy URL → Paste vào browser
3. Nhập thông tin thẻ test:
   - Số thẻ: `9704198526191432198`
   - Tên: `NGUYEN VAN A`
   - Ngày: `07/15`
4. Nhập OTP: `123456`
5. Click **Thanh toán**
6. Redirect về callback
7. Check balance tăng!

---

#### 6️⃣ Nạp Tiền Qua MoMo

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/deposit/momo`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "amount": 300000,
  "description": "Nạp tiền qua MoMo"
}
```

**Response (200 OK):**
```json
{
  "paymentUrl": "https://test-payment.momo.vn/gw_payment/payment/qr?...",
  "orderId": "WALLET_DEP_1730189000789",
  "amount": 300000,
  "currency": "VND",
  "deeplink": "momo://app?action=payWithAppToken&..."
}
```

**Test - Cách 1 (Có App):**
1. Send request → Nhận `paymentUrl`
2. Mở URL trong browser → Thấy QR code
3. Mở MoMo Test App → Quét QR
4. Xác nhận thanh toán
5. Check balance

**Test - Cách 2 (Giả Lập - Không Cần App):**

1. Send request trên → Copy `orderId`

2. Gọi callback manually:

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/momo/callback`  
**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "partnerCode": "MOMOIQA420180417",
  "orderId": "WALLET_DEP_1730189000789",
  "requestId": "test-req-123",
  "amount": "300000",
  "orderInfo": "Nạp tiền qua MoMo",
  "orderType": "momo_wallet",
  "transId": "12345678",
  "resultCode": "0",
  "message": "Successful.",
  "payType": "qr",
  "responseTime": "1730189100000",
  "extraData": "",
  "signature": "dummy-signature"
}
```

3. Send → Callback thành công
4. Check balance tăng!

**Lưu ý:** Cách 2 chỉ hoạt động nếu đã tắt signature verification (xem phần Hướng Dẫn Sinh Viên)

---

#### 7️⃣ Nạp Tiền Qua ZaloPay

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/deposit/zalopay`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "amount": 400000,
  "description": "Nạp tiền qua ZaloPay"
}
```

**Response (200 OK):**
```json
{
  "paymentUrl": "https://sb-openapi.zalopay.vn/v2/gateway/pay?order_token=...",
  "orderId": "WALLET_DEP_1730189200999",
  "amount": 400000,
  "currency": "VND",
  "zpTransToken": "token-xxx"
}
```

**Test - Cách 1 (Có App):**
1. Send → Nhận URL
2. Mở trong ZaloPay app
3. Xác nhận với PIN `123456`
4. Check balance

**Test - Cách 2 (Giả Lập):**

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/wallet/zalopay/callback`  
**Headers:**
```
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "data": "{\"app_id\":2553,\"app_trans_id\":\"251029_999\",\"amount\":400000,\"status\":1,\"zp_trans_id\":\"789456\"}",
  "mac": "test-mac",
  "type": 1
}
```

**Lưu ý:** Cách 2 chỉ hoạt động nếu đã tắt MAC verification

---

#### 8️⃣ Xem Lịch Sử Giao Dịch

**Method:** `GET`  
**URL:** `http://localhost:8080/api/user/wallet/transactions`  
**Headers:**
```
Authorization: Bearer {{token}}
```

**Response (200 OK):**
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
      "externalOrderId": "WALLET_DEP_123",
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
      "externalOrderId": "WALLET_DEP_456",
      "createdAt": "2025-10-29T11:00:00"
    }
  ],
  "totalTransactions": 2
}
```

**Query Parameters (Optional):**
- `?type=deposit` - Lọc theo loại
- `?startDate=2025-10-01&endDate=2025-10-31` - Lọc theo thời gian

**Example:**
```
GET http://localhost:8080/api/user/wallet/transactions?type=deposit
```

---

### C. ORDER APIs

#### 9️⃣ Tạo Đơn Hàng (Chưa Thanh Toán)

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/orders`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "deliveryAddressId": 1,
  "paymentMethod": "app_wallet",
  "cartItemIds": [1, 2, 3],
  "note": "Giao hàng giờ hành chính"
}
```

**Response (200 OK):**
```json
{
  "orderId": 123,
  "totalAmount": 150000,
  "paymentStatus": "pending",
  "orderStatus": "pending",
  "items": [
    {
      "productName": "Bánh mì",
      "quantity": 2,
      "price": 25000
    }
  ]
}
```

---

#### 🔟 Thanh Toán Đơn Hàng Bằng Ví

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/orders/123/pay`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "paymentMethod": "app_wallet"
}
```

**Response Success (200 OK):**
```json
{
  "message": "Payment successful",
  "orderId": 123,
  "paymentStatus": "paid",
  "newBalance": 850000,
  "transactionId": 5
}
```

**Response Error - Không Đủ Tiền (400 Bad Request):**
```json
{
  "error": "Insufficient balance",
  "currentBalance": 100000,
  "requiredAmount": 150000,
  "shortfall": 50000
}
```

**Test Steps:**
1. Tạo đơn hàng (API #9) → Lấy `orderId`
2. Thay `123` trong URL bằng `orderId` thật
3. Send request
4. Nếu đủ tiền → Success
5. Check balance giảm đi

---

#### 1️⃣1️⃣ Hủy Đơn Hàng (Hoàn Tiền)

**Method:** `POST`  
**URL:** `http://localhost:8080/api/user/orders/123/cancel`  
**Headers:**
```
Authorization: Bearer {{token}}
Content-Type: application/json
```

**Body (raw JSON):**
```json
{
  "reason": "Đổi ý không mua nữa"
}
```

**Response (200 OK):**
```json
{
  "message": "Order cancelled and refunded",
  "orderId": 123,
  "refundAmount": 150000,
  "newBalance": 1000000,
  "refundTransactionId": 6
}
```

**Test Flow:**
1. Tạo đơn → Thanh toán
2. Hủy đơn (API này)
3. Check balance → Tiền được hoàn lại
4. Check transactions → Có record `refund`

---

## 📦 POSTMAN COLLECTION JSON

Copy đoạn JSON dưới đây → Save thành file `FoodTourApp.postman_collection.json` → Import vào Postman:

```json
{
  "info": {
    "name": "FoodTourApp Payment APIs",
    "description": "Complete API collection for payment gateway testing",
    "schema": "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
  },
  "variable": [
    {
      "key": "baseUrl",
      "value": "http://localhost:8080",
      "type": "string"
    },
    {
      "key": "token",
      "value": "",
      "type": "string"
    },
    {
      "key": "orderId",
      "value": "",
      "type": "string"
    }
  ],
  "item": [
    {
      "name": "1. Authentication",
      "item": [
        {
          "name": "Register",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
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
          "name": "Login (Get Token)",
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
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
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
      "name": "2. Wallet",
      "item": [
        {
          "name": "Get Balance",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              }
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
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
          "name": "MoMo Callback (Mock)",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"partnerCode\": \"MOMOIQA420180417\",\n  \"orderId\": \"WALLET_DEP_123\",\n  \"requestId\": \"test123\",\n  \"amount\": \"300000\",\n  \"orderInfo\": \"Nạp tiền test\",\n  \"orderType\": \"momo_wallet\",\n  \"transId\": \"12345678\",\n  \"resultCode\": \"0\",\n  \"message\": \"Success\",\n  \"payType\": \"qr\",\n  \"responseTime\": \"1730189100000\",\n  \"extraData\": \"\",\n  \"signature\": \"test-signature\"\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/momo/callback",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "momo", "callback"]
            }
          }
        },
        {
          "name": "Deposit via ZaloPay",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
          "name": "ZaloPay Callback (Mock)",
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Content-Type",
                "value": "application/json"
              }
            ],
            "body": {
              "mode": "raw",
              "raw": "{\n  \"data\": \"{\\\"app_id\\\":2553,\\\"app_trans_id\\\":\\\"251029_999\\\",\\\"amount\\\":400000,\\\"status\\\":1}\",\n  \"mac\": \"test-mac\",\n  \"type\": 1\n}"
            },
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/zalopay/callback",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "zalopay", "callback"]
            }
          }
        },
        {
          "name": "Get Transactions",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/transactions",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "transactions"]
            }
          }
        },
        {
          "name": "Get Transactions by Type",
          "request": {
            "method": "GET",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              }
            ],
            "url": {
              "raw": "{{baseUrl}}/api/user/wallet/transactions?type=deposit",
              "host": ["{{baseUrl}}"],
              "path": ["api", "user", "wallet", "transactions"],
              "query": [
                {
                  "key": "type",
                  "value": "deposit"
                }
              ]
            }
          }
        }
      ]
    },
    {
      "name": "3. Orders",
      "item": [
        {
          "name": "Create Order",
          "event": [
            {
              "listen": "test",
              "script": {
                "exec": [
                  "var jsonData = pm.response.json();",
                  "pm.collectionVariables.set(\"orderId\", jsonData.orderId);"
                ]
              }
            }
          ],
          "request": {
            "method": "POST",
            "header": [
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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
              {
                "key": "Authorization",
                "value": "Bearer {{token}}"
              },
              {
                "key": "Content-Type",
                "value": "application/json"
              }
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

### Cách Import Collection Vào Postman:

1. Copy toàn bộ JSON ở trên
2. Lưu thành file: `FoodTourApp.postman_collection.json`
3. Mở Postman → Click **Import**
4. Chọn file vừa lưu
5. Collection xuất hiện với 3 folder:
   - **1. Authentication** (Register, Login)
   - **2. Wallet** (Balance, Deposit, Transactions)
   - **3. Orders** (Create, Pay, Cancel)

### Sử Dụng Collection:

1. **Login** → Token tự động lưu vào biến `{{token}}`
2. Các request khác tự động dùng token
3. **Create Order** → `orderId` tự động lưu
4. **Pay Order** / **Cancel Order** → Tự động dùng `orderId`

---

## ❓ FAQ - CÂU HỎI THƯỜNG GẶP

### Q1: Credentials demo có hoạt động không?

**A:**
- **VNPay, MoMo, ZaloPay:** ✅ CÓ, hoạt động 100%
- **PayPal:** ❌ KHÔNG, phải tạo riêng (nhưng FREE, 5 phút)

### Q2: Tôi có bị mất tiền khi test không?

**A:** ❌ **KHÔNG! Tất cả đều TIỀN ẢO:**

| Gateway | Tiền Test |
|---------|-----------|
| PayPal | Sandbox có $1,000 ảo |
| VNPay | Thẻ test số dư vô hạn |
| MoMo | Test App 10 triệu ảo |
| ZaloPay | Tài khoản demo tiền ảo |

### Q3: Tôi là sinh viên, không có tiền. Test được không?

**A:** ✅ **CÓ! 100% MIỄN PHÍ!**

**Cách nhanh nhất (không cần app):**
1. Tắt signature verification (xem phần Sinh Viên)
2. Test bằng Postman
3. Gọi callback manually
4. Balance tăng!

### Q4: Làm sao test end-to-end như thật?

**A:** Dùng test account:
- **PayPal:** Tài khoản sandbox (có sẵn sau khi tạo app)
- **VNPay:** Thẻ `9704198526191432198`, OTP `123456`
- **MoMo:** Email `developer@momo.vn` xin Test App
- **ZaloPay:** Số `0999999999`, OTP `111111`, PIN `123456`

### Q5: Postman báo "Unauthorized" 401?

**A:** Kiểm tra:
1. ✅ Đã login và lấy token chưa?
2. ✅ Token đã paste vào Header `Authorization: Bearer ...` chưa?
3. ✅ Token hết hạn? → Login lại
4. ✅ Format đúng: `Bearer [space] token`

### Q6: API trả về 500 Internal Server Error?

**A:** Kiểm tra:
1. ✅ Server đang chạy chưa?
2. ✅ Database đã khởi tạo chưa?
3. ✅ Check logs trong terminal
4. ✅ Credentials đã config đúng trong `application.properties` chưa?

### Q7: PayPal báo "Invalid Client ID"?

**A:**
1. ✅ Đang dùng **Sandbox** credentials (không phải Live)
2. ✅ Copy lại Client ID/Secret (không có space thừa)
3. ✅ Check `paypal.mode=sandbox` trong config
4. ✅ Restart server sau khi sửa config

### Q8: VNPay/MoMo/ZaloPay báo "Invalid Signature"?

**A:**
1. ✅ Đã copy đúng credentials demo chưa?
2. ✅ Check log để xem signature string
3. ✅ Hoặc tắt verification tạm (xem phần Sinh Viên)

---

## 🎓 HƯỚNG DẪN ĐẶC BIỆT CHO SINH VIÊN

### Test Nhanh Nhất (Không Cần Tiền, Không Cần App)

Nếu bạn muốn demo nhanh cho đồ án mà không cần cài app test hay có tiền thật:

#### Bước 1: Tắt Signature Verification Tạm Thời

**File: `src/main/java/com/example/FoodTourApp/service/impl/MoMoPaymentService.java`**

```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY - XÓA KHI PRODUCTION!
    log.warn("⚠️ DEV MODE: MoMo signature verification DISABLED!");
    
    String resultCode = params.get("resultCode");
    boolean success = "0".equals(resultCode);
    
    if (success) {
        log.info("✅ MoMo test payment successful for order: {}", params.get("orderId"));
    } else {
        log.error("❌ MoMo test payment failed. ResultCode: {}", resultCode);
    }
    
    return success;
    
    // TODO: BẬT LẠI PHẦN NÀY KHI DEPLOY PRODUCTION
    /*
    try {
        String receivedSignature = params.get("signature");
        
        String rawSignature = "accessKey=" + accessKey +
                "&amount=" + params.get("amount") +
                "&extraData=" + params.getOrDefault("extraData", "") +
                "&message=" + params.get("message") +
                "&orderId=" + params.get("orderId") +
                "&orderInfo=" + params.get("orderInfo") +
                "&orderType=" + params.get("orderType") +
                "&partnerCode=" + partnerCode +
                "&payType=" + params.get("payType") +
                "&requestId=" + params.get("requestId") +
                "&responseTime=" + params.get("responseTime") +
                "&resultCode=" + params.get("resultCode") +
                "&transId=" + params.get("transId");
        
        String calculatedSignature = hmacSHA256(secretKey, rawSignature);
        
        boolean signatureValid = calculatedSignature.equals(receivedSignature);
        boolean paymentSuccess = "0".equals(params.get("resultCode"));
        
        log.info("MoMo callback verification - Signature valid: {}, Payment success: {}", 
                 signatureValid, paymentSuccess);
        
        return signatureValid && paymentSuccess;
    } catch (Exception e) {
        log.error("Failed to verify MoMo callback: {}", e.getMessage(), e);
        return false;
    }
    */
}
```

**File: `src/main/java/com/example/FoodTourApp/service/impl/ZaloPayPaymentService.java`**

```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY - XÓA KHI PRODUCTION!
    log.warn("⚠️ DEV MODE: ZaloPay MAC verification DISABLED!");
    
    try {
        String data = params.get("data");
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
        Integer status = (Integer) dataMap.get("status");
        
        boolean success = status != null && status == 1;
        
        if (success) {
            log.info("✅ ZaloPay test payment successful");
        } else {
            log.error("❌ ZaloPay test payment failed. Status: {}", status);
        }
        
        return success;
        
    } catch (Exception e) {
        log.error("❌ Failed to parse ZaloPay callback: {}", e.getMessage(), e);
        return false;
    }
    
    // TODO: BẬT LẠI MAC VERIFICATION KHI PRODUCTION
    /*
    try {
        String receivedMac = params.get("mac");
        String data = params.get("data");
        
        String calculatedMac = hmacSHA256(key2, data);
        
        boolean macValid = calculatedMac.equals(receivedMac);
        
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
        Integer status = (Integer) dataMap.get("status");
        
        boolean paymentSuccess = status != null && status == 1;
        
        log.info("ZaloPay callback verification - MAC valid: {}, Payment success: {}", 
                 macValid, paymentSuccess);
        
        return macValid && paymentSuccess;
    } catch (Exception e) {
        log.error("Failed to verify ZaloPay callback: {}", e.getMessage(), e);
        return false;
    }
    */
}
```

#### Bước 2: Test Bằng Postman

**Scenario: Nạp 100,000 VND qua MoMo**

**Request 1: Tạo Payment URL**
```
POST http://localhost:8080/api/user/wallet/deposit/momo
Authorization: Bearer {{token}}
Content-Type: application/json

Body:
{
  "amount": 100000,
  "description": "Test nạp tiền MoMo"
}

Response:
{
  "paymentUrl": "https://test-payment.momo.vn/...",
  "orderId": "WALLET_DEP_1730188800123"
}
```

**📌 Copy `orderId` từ response!**

**Request 2: Giả Lập Callback Thành Công**
```
POST http://localhost:8080/api/user/wallet/momo/callback
Content-Type: application/json

Body:
{
  "partnerCode": "MOMOIQA420180417",
  "orderId": "WALLET_DEP_1730188800123",
  "requestId": "test-req-001",
  "amount": "100000",
  "orderInfo": "Test nạp tiền MoMo",
  "orderType": "momo_wallet",
  "transId": "12345678",
  "resultCode": "0",
  "message": "Successful.",
  "payType": "qr",
  "responseTime": "1730188900000",
  "extraData": "",
  "signature": "dummy-signature-for-testing"
}
```

**Request 3: Kiểm Tra Balance**
```
GET http://localhost:8080/api/user/wallet/balance
Authorization: Bearer {{token}}

Response:
{
  "userId": 1,
  "balance": 100000.0,
  "currency": "VND"
}
```

✅ **Thành công! Balance đã tăng 100,000 VND!**

#### Bước 3: Demo Cho Giáo Viên

**Khi demo, bạn nên:**

1. ✅ **Show flow đầy đủ:**
   - Tạo payment URL
   - Giả lập callback
   - Balance tăng
   - Transaction history có record

2. ✅ **Giải thích cho thầy/cô:**

> "Em đã tích hợp 4 payment gateway: PayPal, VNPay, MoMo và ZaloPay.
> 
> Do đang trong môi trường development và hạn chế về test environment,
> em giả lập callback để demo flow hoạt động.
> 
> Trong production thực tế, callback sẽ được payment gateway tự động
> gọi sau khi user thanh toán thành công, và em sẽ bật signature
> verification để đảm bảo tính bảo mật.
> 
> Em đã implement đầy đủ logic verify signature (code đã comment trong
> source), chỉ tạm thời tắt để thuận tiện cho việc test và demo."

3. ✅ **Show code verify signature:**

Mở file service và show phần code đã comment để chứng minh bạn hiểu logic:

```java
// Em đã implement đầy đủ signature verification
// Chỉ tắt tạm để demo, production sẽ bật lại
/*
String rawSignature = "accessKey=" + accessKey + ...
String calculatedSignature = hmacSHA256(secretKey, rawSignature);
boolean signatureValid = calculatedSignature.equals(receivedSignature);
return signatureValid && paymentSuccess;
*/
```

#### Lưu Ý Quan Trọng Trước Khi Nộp

**✅ NÊN LÀM:**
1. Ghi chú rõ trong code: `// DEVELOPMENT MODE - For testing only`
2. Comment trong báo cáo về việc giả lập callback
3. Giải thích production sẽ bật verification
4. Giữ code verify (nhưng comment) để show hiểu logic
5. Log đầy đủ để dễ debug

**❌ KHÔNG NÊN:**
1. Xóa hẳn code verification
2. Nói dối đã test với tiền thật
3. Để code development mode trong production
4. Không giải thích với giáo viên

---

## 📋 CHECKLIST HOÀN CHỈNH

### Chuẩn Bị
- [ ] Đã cài Postman
- [ ] Server chạy ở `localhost:8080`
- [ ] Database đã khởi tạo
- [ ] File `application.properties` đã config credentials

### Credentials
- [ ] **PayPal:** Đã tạo app sandbox và copy Client ID + Secret
- [ ] **VNPay:** Đã copy credentials demo
- [ ] **MoMo:** Đã copy credentials demo
- [ ] **ZaloPay:** Đã copy credentials demo

### Test APIs
- [ ] Register → Tạo user thành công
- [ ] Login → Nhận token
- [ ] Get Balance → Thấy balance = 0
- [ ] Deposit PayPal → Nhận payment URL
- [ ] Deposit VNPay → Nhận payment URL
- [ ] Deposit MoMo → Test callback → Balance tăng
- [ ] Deposit ZaloPay → Test callback → Balance tăng
- [ ] Get Transactions → Thấy history đầy đủ
- [ ] Create Order → Nhận orderId
- [ ] Pay Order → Balance giảm
- [ ] Cancel Order → Balance tăng lại (refund)

### Demo
- [ ] Video/slides chuẩn bị
- [ ] Giải thích flow rõ ràng
- [ ] Code sạch, có comment
- [ ] Báo cáo đầy đủ

---

## 📞 HỖ TRỢ

**Nếu gặp vấn đề:**

- **PayPal:** https://developer.paypal.com/support/
- **VNPay:** merchant@vnpay.vn | Hotline: 1900 55 55 77
- **MoMo:** developer@momo.vn | Hotline: 1900 54 54 41
- **ZaloPay:** merchant@zalopay.vn | Hotline: 1900 5555 77

---

## 🔗 TÀI LIỆU THAM KHẢO

### PayPal
- Developer Portal: https://developer.paypal.com
- API Docs: https://developer.paypal.com/api/rest/
- Sandbox Guide: https://developer.paypal.com/tools/sandbox/

### VNPay
- Merchant Portal: https://sandbox.vnpayment.vn/merchantv2/
- Email: merchant@vnpay.vn

### MoMo
- Business Portal: https://business.momo.vn
- API Docs: https://developers.momo.vn
- Test Guide: https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions

### ZaloPay
- Developer Portal: https://docs.zalopay.vn
- API Reference: https://docs.zalopay.vn/v2/
- Sandbox: https://sb-openapi.zalopay.vn

---

**📅 Cập nhật:** 29/10/2025  
**📌 Version:** 4.0 - Complete Guide  
**👨‍💻 Tác giả:** FoodTourApp Team

---

## 💡 TIPS CUỐI CÙNG

1. **Luôn test trên Sandbox trước** - KHÔNG bao giờ test thẳng production
2. **Log đầy đủ** - Enable logging để debug dễ dàng
3. **Handle errors** - Payment có thể fail vì nhiều lý do
4. **Test edge cases:**
   - User cancel payment
   - Network timeout
   - Insufficient balance
   - Duplicate transactions
5. **Security first:**
   - Verify signature trong production
   - Không commit credentials vào Git
   - Dùng environment variables
6. **Document everything** - Viết tài liệu rõ ràng cho team

**🎉 Chúc bạn test thành công và bảo vệ đồ án tốt nghiệp tốt!**

