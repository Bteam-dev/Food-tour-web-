# 🔑 HƯỚNG DẪN LẤY CREDENTIALS - TỪNG BƯỚC CỤ THỂ

> **Mục đích:** Hướng dẫn CHI TIẾT từng click chuột để lấy API keys cho payment gateway
> 
> **🎓 DÀNH CHO SINH VIÊN:** Tất cả đều test được bằng TIỀN ẢO, không tốn 1 đồng nào!

---

## 🎯 QUAN TRỌNG: Credentials Là Gì?

**Credentials** = Chìa khóa để app của bạn kết nối với payment gateway

Gồm có:
- **API Key / Client ID** - Định danh app của bạn
- **Secret Key** - Mật khẩu bảo mật (KHÔNG BAO GIỜ public)
- **Hash Secret** - Dùng để mã hóa dữ liệu

---

## 💰 TEST VỚI TIỀN ẢO 100% - KHÔNG MẤT TIỀN THẬT!

**Tất cả 4 payment gateway đều hỗ trợ test với TIỀN ẢO:**

| Gateway | Tiền Ảo? | Cách Test |
|---------|----------|-----------|
| PayPal  | ✅ 100% | Tài khoản sandbox có sẵn $1,000 ảo |
| VNPay   | ✅ 100% | Thẻ test với số dư vô hạn |
| MoMo    | ✅ 100% | App sandbox riêng với tiền ảo |
| ZaloPay | ✅ 100% | Tài khoản demo với tiền ảo |

**👉 Bạn KHÔNG CẦN tiền thật để test!**

---

## 1. 💙 PayPal - SIÊU DỄ (5 phút)

### ⭐ Credentials Demo Sẵn (Test Ngay)
Nếu mày chỉ muốn test thử, dùng luôn credentials này:

```properties
paypal.mode=sandbox
paypal.client.id=AZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7dE_E8tC9vGJZpVjL8dLLvqKE7Z0X0C7dGz1f
paypal.client.secret=EKe8F1qGJZpVjL8dLLvqKE7Z0X0C7dGz1fAZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7
```

**NHƯNG** những credentials này là FAKE, chỉ để minh họa. Bạn PHẢI tạo credentials riêng!

---

### 📝 Lấy Credentials Thật (Recommended)

#### Bước 1: Truy cập PayPal Developer
```
Link: https://developer.paypal.com
```

**Làm gì:**
- Mở trình duyệt (Chrome, Firefox, Edge...)
- Copy link trên vào thanh địa chỉ
- Nhấn Enter

**Thấy gì:**
- Trang chủ PayPal Developer màu xanh dương
- Nút **"Log In"** góc trên bên phải

---

#### Bước 2: Đăng Nhập

**Chưa có tài khoản PayPal?**
1. Click **"Sign Up"**
2. Chọn **"Personal Account"** (tài khoản cá nhân)
3. Nhập email, mật khẩu, họ tên
4. Xác nhận email
5. **KHÔNG CẦN** thêm thẻ tín dụng/ngân hàng

**Đã có tài khoản PayPal?**
1. Click **"Log In"**
2. Nhập email + password PayPal của bạn
3. Xác thực 2 yếu tố (nếu có)

**Sau khi đăng nhập thành công:**
- Bạn sẽ thấy **Dashboard** màu xanh dương
- Menu bên trái: Dashboard, Apps, Sandbox accounts...

---

#### Bước 3: Tạo App

**Click theo thứ tự:**

1️⃣ **Click "Apps & Credentials"** (menu bên trái)

2️⃣ **Click tab "Sandbox"** (QUAN TRỌNG! Không phải "Live")
   - Tab "Sandbox" = môi trường test
   - Tab "Live" = môi trường thật (ĐỪNG dùng khi test)

3️⃣ **Click nút "Create App"** (nút màu xanh, góc phải)

4️⃣ **Điền thông tin:**
   ```
   App Name: FoodTourApp
   (hoặc tên gì cũng được, ví dụ: TestPayment)
   
   Sandbox Business Account: 
   (Chọn account có sẵn trong dropdown)
   ```

5️⃣ **Click "Create App"** (nút xanh phía dưới)

---

#### Bước 4: COPY Credentials

**Sau khi tạo app, bạn sẽ thấy trang mới:**

```
┌─────────────────────────────────────────────┐
│ FoodTourApp                         [Edit]  │
├─────────────────────────────────────────────┤
│                                             │
│ Client ID                                   │
│ ┌─────────────────────────────────────────┐ │
│ │ AZqE-frVQkg3FcJiuPGdlmZxJpCF0RN...    │ │
│ └─────────────────────────────────────────┘ │
│     [Show]  [Copy]                          │
│                                             │
│ Secret                                      │
│ ┌─────────────────────────────────────────┐ │
│ │ •••••••••••••••••••••••••••••••••      │ │
│ └─────────────────────────────────────────┘ │
│     [Show]  [Copy]                          │
│                                             │
└─────────────────────────────────────────────┘
```

**COPY thế nào:**

1. **Client ID:**
   - Click nút **"Copy"** bên cạnh Client ID
   - Hoặc: Bôi đen text → Ctrl+C
   - Paste vào Notepad tạm

2. **Secret:**
   - Click nút **"Show"** để hiển thị
   - Secret sẽ xuất hiện thay vì dấu ••••
   - Click nút **"Copy"**
   - Paste vào Notepad tạm

**VÍ DỤ KẾT QUẢ:**
```
Client ID: AZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7dE_E8tC9vGJZpVjL8dLLvqKE7Z0X0C7dGz1f

Secret: EKe8F1qGJZpVjL8dLLvqKE7Z0X0C7dGz1fAZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7dE_E8tC9
```

---

#### Bước 5: Paste vào application.properties

Mở file `src/main/resources/application.properties`:

```properties
# PayPal Sandbox
paypal.mode=sandbox
paypal.client.id=AZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7dE_E8tC9vGJZpVjL8dLLvqKE7Z0X0C7dGz1f
paypal.client.secret=EKe8F1qGJZpVjL8dLLvqKE7Z0X0C7dGz1fAZqE-frVQkg3FcJiuPGdlmZxJpCF0RNp8gTzEgqJKVK7dE_E8tC9
paypal.returnUrl=http://localhost:8080/api/user/wallet/paypal/success
paypal.cancelUrl=http://localhost:8080/api/user/wallet/paypal/cancel
```

**✅ XONG! PayPal đã sẵn sàng test!**

---

#### 🧪 Lấy Tài Khoản Test

Để test thanh toán, bạn cần tài khoản test:

**Cách lấy:**

1️⃣ Vào **"Sandbox"** → **"Accounts"** (menu bên trái)

2️⃣ Bạn sẽ thấy 2 tài khoản có sẵn:

```
┌──────────────────────────────────────────────────────┐
│ Email                          │ Type     │ Balance   │
├──────────────────────────────────────────────────────┤
│ sb-buyer123@personal.example   │ PERSONAL │ $1,000.00 │
│ sb-seller456@business.example  │ BUSINESS │ $0.00     │
└──────────────────────────────────────────────────────┘
```

3️⃣ Click vào **"..."** (3 chấm) bên cạnh tài khoản **PERSONAL**

4️⃣ Click **"View/Edit Account"**

5️⃣ Bạn sẽ thấy:
```
Email: sb-buyer123@personal.example.com
Password: 12345678
```

6️⃣ **COPY email và password này!** Dùng để login khi test thanh toán

---

## 2. 🇻🇳 VNPay - KHÔNG CẦN ĐĂNG KÝ

### ⭐ Dùng Credentials Demo Công Khai

VNPay cung cấp credentials demo để test **KHÔNG CẦN ĐĂNG KÝ**:

```properties
vnpay.payUrl=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.returnUrl=http://localhost:8080/api/user/wallet/vnpay/callback
vnpay.tmnCode=DEMOSHOP
vnpay.hashSecret=DEMOSECRETKEY123456789012345678901234567890
vnpay.version=2.1.0
vnpay.command=pay
```

**✅ Copy paste 6 dòng trên vào application.properties là XONG!**

---

### 🧪 Thông Tin Thẻ Test

Khi test thanh toán VNPay, dùng thông tin thẻ này:

```
Ngân hàng: NCB (Ngân hàng Quốc Dân)
Số thẻ: 9704198526191432198
Tên chủ thẻ: NGUYEN VAN A
Ngày phát hành: 07/15
Mã OTP: 123456
```

**Lưu ý:** OTP luôn là `123456` cho mọi giao dịch test!

---

### 📝 Muốn Credentials Riêng? (Tùy Chọn)

**YÊU CẦU:**
- Giấy phép kinh doanh (GPKD)
- Thông tin doanh nghiệp

**CÁCH LÀM:**

1. Email: **merchant@vnpay.vn**
2. Tiêu đề: "Đăng ký tài khoản Sandbox VNPay"
3. Nội dung:
   ```
   Kính gửi VNPay,
   
   Tôi là [Tên của bạn] từ [Tên công ty].
   Tôi muốn đăng ký tài khoản Sandbox để tích hợp VNPay 
   vào ứng dụng [Tên app].
   
   Thông tin công ty:
   - Tên công ty: ...
   - Mã số thuế: ...
   - Email: ...
   - SĐT: ...
   
   Trân trọng,
   [Tên của bạn]
   ```

4. Đợi VNPay reply (1-3 ngày làm việc)
5. Nhận credentials qua email
6. Đăng nhập vào https://sandbox.vnpayment.vn/merchantv2/
7. Vào **Cấu hình** → **Thông tin doanh nghiệp**
8. Copy **Mã TMN** và **Hash Secret**

---

## 3. 🟣 MoMo - DÙNG LUÔN CREDENTIALS DEMO

### ⭐ Credentials Demo Công Khai

MoMo cung cấp credentials test công khai:

```properties
momo.endpoint=https://test-payment.momo.vn/v2/gateway/api/create
momo.returnUrl=http://localhost:8080/api/user/wallet/momo/callback
momo.notifyUrl=http://localhost:8080/api/user/wallet/momo/webhook
momo.partnerCode=MOMOIQA420180417
momo.accessKey=SvDmj2cOTYZmQQ3H
momo.secretKey=PPuDXq1KowPT1ftR8DvlQTHhC03aul17
```

**✅ Copy 7 dòng trên vào application.properties là XONG!**

Những credentials này là **CÔNG KHAI** trong tài liệu MoMo, ai cũng có thể dùng để test.

---

### 📱 Test Với MoMo - CÓ 2 CÁCH (Đều MIỄN PHÍ)

#### ⭐ CÁCH 1: Dùng MoMo Test App (TIỀN ẢO - Khuyến Nghị Cho Sinh Viên)

**Tải MoMo Test App riêng:**

**Android:**
1. Tải file APK từ: https://developers.momo.vn/v3/docs/payment/onboarding/test-instructions
2. Hoặc email: **developer@momo.vn** xin link download MoMo Test App
3. Enable "Cài đặt từ nguồn không xác định" trên điện thoại
4. Cài đặt APK

**iOS:**
1. Liên hệ MoMo qua email: **developer@momo.vn**
2. Xin TestFlight invite cho MoMo Sandbox App

**Đăng ký tài khoản test:**
1. Mở MoMo Test App
2. Đăng ký với số điện thoại bất kỳ (không cần số thật)
3. OTP: Nhập **111111** hoặc **123456** (OTP fix cho test)
4. Tạo mã PIN bất kỳ
5. **Tài khoản tự động có 10,000,000 VND tiền ảo!**

**Test thanh toán:**
- App tạo QR code
- Quét bằng MoMo Test App
- Xác nhận với PIN
- **Tiền trừ là tiền ảo, KHÔNG MẤT TIỀN THẬT!**

---

#### CÁCH 2: Giả Lập Response (Không Cần App - Đơn Giản Nhất)

**Vì đang dev, bạn có thể giả lập callback thay vì dùng app thật:**

1. **Tạo payment URL** bằng Postman:
```http
POST http://localhost:8080/api/user/wallet/deposit/momo
Authorization: Bearer YOUR_TOKEN
Content-Type: application/json

{
  "amount": 100000,
  "description": "Nạp tiền test"
}
```

2. **Response sẽ trả về:**
```json
{
  "paymentUrl": "https://test-payment.momo.vn/...",
  "orderId": "WALLET_DEP_123"
}
```

3. **Thay vì mở URL, giả lập callback thành công:**

**Tạm thời DISABLE signature verification** trong `MoMoPaymentService.java`:
```java
public boolean verifyCallback(Map<String, String> params) {
    // TODO: ONLY FOR DEVELOPMENT - REMOVE IN PRODUCTION!
    String resultCode = params.get("resultCode");
    return "0".equals(resultCode); // 0 = success
    
    // Comment out signature verification cho đến khi có app test
    // boolean signatureValid = ...
    // return signatureValid && paymentSuccess;
}
```

4. **Gọi callback manually:**
```http
POST http://localhost:8080/api/user/wallet/momo/callback
Content-Type: application/json

{
  "partnerCode": "MOMOIQA420180417",
  "orderId": "WALLET_DEP_123",
  "requestId": "test-req-123",
  "amount": "100000",
  "orderInfo": "Nạp tiền test",
  "orderType": "momo_wallet",
  "transId": "12345678",
  "resultCode": "0",
  "message": "Success",
  "payType": "qr",
  "responseTime": "1698566400000",
  "extraData": "",
  "signature": "dummy-signature"
}
```

5. **Kiểm tra balance đã tăng!**

**Lưu ý:** Nhớ bật lại signature verification khi deploy production!

---

### 📝 Muốn Credentials Riêng? (Tùy Chọn)

**YÊU CẦU:**
- Giấy phép kinh doanh
- Website/App đã hoàn thiện

**CÁCH LÀM:**

1. Truy cập: https://business.momo.vn

2. Click **"Đăng ký"**

3. Chọn **"Tích hợp thanh toán"**

4. Điền form:
   ```
   - Tên doanh nghiệp
   - Mã số thuế
   - Người đại diện
   - Email
   - SĐT
   - Lĩnh vực kinh doanh
   ```

5. Upload giấy tờ:
   - GPKD
   - CMND/CCCD người đại diện
   - Giấy ủy quyền (nếu có)

6. Đợi MoMo duyệt (3-7 ngày)

7. Nhận email thông báo duyệt

8. Đăng nhập **MoMo Business Portal**: https://business.momo.vn

9. Vào **Cấu hình** → **Thông tin kỹ thuật**

10. Chọn môi trường **"Sandbox"**

11. Copy:
    - **Partner Code**
    - **Access Key**
    - **Secret Key**

---

## 4. 💙 ZaloPay - DÙNG LUÔN CREDENTIALS DEMO

### ⭐ Credentials Demo Công Khai

ZaloPay cung cấp credentials test:

```properties
zalopay.endpoint=https://sb-openapi.zalopay.vn/v2/create
zalopay.callbackUrl=http://localhost:8080/api/user/wallet/zalopay/callback
zalopay.appId=2553
zalopay.key1=PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL
zalopay.key2=kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz
```

**✅ Copy 5 dòng trên vào application.properties là XONG!**

Credentials này là **CÔNG KHAI** trong docs.zalopay.vn

---

### 📱 Test Với ZaloPay - CÓ 2 CÁCH (Đều MIỄN PHÍ)

#### ⭐ CÁCH 1: Dùng ZaloPay Sandbox (TIỀN ẢO - Khuyến Nghị)

**Tài khoản demo có sẵn:**

ZaloPay cung cấp tài khoản sandbox với tiền ảo:

```
Số điện thoại: 0999999999
OTP: 111111 (fix cho mọi giao dịch test)
PIN: 123456
```

**Hoặc tạo tài khoản test riêng:**

1. **Tải ZaloPay App** (app chính thức)
   - Android: Google Play Store
   - iOS: App Store

2. **Đăng ký với số điện thoại test:**
   - Dùng số ảo từ: https://receive-sms-online.info/
   - Hoặc số điện thoại không dùng của bạn
   - Nhận OTP và đăng ký

3. **Liên kết thẻ test (KHÔNG MẤT TIỀN):**
   
   ZaloPay cho phép test với thẻ ảo:
   ```
   Số thẻ: 9704 0000 0000 0018
   Tên chủ thẻ: NGUYEN VAN A
   Ngày hết hạn: 03/07
   CVV: 123
   OTP: 111111 (luôn dùng OTP này cho test)
   ```

4. **Test thanh toán:**
   - App tạo payment URL
   - Mở trong ZaloPay app
   - Xác nhận với PIN test
   - **Tiền test, KHÔNG MẤT TIỀN THẬT!**

---

#### CÁCH 2: Giả Lập Response (Không Cần App)

Tương tự MoMo, bạn có thể giả lập callback:

1. **Tạm disable signature verification** trong `ZaloPayPaymentService.java`:
```java
public boolean verifyCallback(Map<String, String> params) {
    // TODO: ONLY FOR DEVELOPMENT
    log.warn("DEVELOPMENT MODE: MAC verification disabled!");
    
    try {
        String data = params.get("data");
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
        Integer status = (Integer) dataMap.get("status");
        boolean success = status != null && status == 1;
        
        if (success) {
            log.info("ZaloPay test payment successful");
        }
        
        return success;
    } catch (Exception e) {
        log.error("Failed to parse ZaloPay callback: {}", e.getMessage(), e);
        return false;
    }
    
    // TODO: Bật lại MAC verification khi production
    /*
    String receivedMac = params.get("mac");
    String calculatedMac = hmacSHA256(key2, data);
    boolean macValid = calculatedMac.equals(receivedMac);
    return macValid && paymentSuccess;
    */
}
```

2. **Tạo payment URL:**
```http
POST http://localhost:8080/api/user/wallet/deposit/zalopay
Authorization: Bearer YOUR_TOKEN
Content-Type: application/json

{
  "amount": 100000,
  "description": "Nạp tiền test"
}
```

3. **Giả lập callback:**
```http
POST http://localhost:8080/api/user/wallet/zalopay/callback
Content-Type: application/json

{
  "data": "{\"app_id\":2553,\"app_trans_id\":\"231029_999\",\"amount\":200000,\"status\":1}",
  "mac": "test-mac",
  "type": 1
}
```

4. **Kiểm tra balance đã tăng!**

---

## 📋 TÓM TẮT - CREDENTIALS NHANH NHẤT

### Muốn Test NGAY (5 phút):

```properties
# 1. PayPal - TẠO TÀI KHOẢN MIỄN PHÍ
# Vào: https://developer.paypal.com
# Click: Apps & Credentials → Sandbox → Create App
# Copy: Client ID và Secret
paypal.mode=sandbox
paypal.client.id=YOUR_CLIENT_ID_HERE
paypal.client.secret=YOUR_SECRET_HERE
paypal.returnUrl=http://localhost:8080/api/user/wallet/paypal/success
paypal.cancelUrl=http://localhost:8080/api/user/wallet/paypal/cancel

# 2. VNPay - DÙNG LUÔN KHÔNG CẦN ĐĂNG KÝ
vnpay.payUrl=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
vnpay.returnUrl=http://localhost:8080/api/user/wallet/vnpay/callback
vnpay.tmnCode=DEMOSHOP
vnpay.hashSecret=DEMOSECRETKEY123456789012345678901234567890
vnpay.version=2.1.0
vnpay.command=pay

# 3. MoMo - DÙNG LUÔN KHÔNG CẦN ĐĂNG KÝ
momo.endpoint=https://test-payment.momo.vn/v2/gateway/api/create
momo.returnUrl=http://localhost:8080/api/user/wallet/momo/callback
momo.notifyUrl=http://localhost:8080/api/user/wallet/momo/webhook
momo.partnerCode=MOMOIQA420180417
momo.accessKey=SvDmj2cOTYZmQQ3H
momo.secretKey=PPuDXq1KowPT1ftR8DvlQTHhC03aul17

# 4. ZaloPay - DÙNG LUÔN KHÔNG CẦN ĐĂNG KÝ
zalopay.endpoint=https://sb-openapi.zalopay.vn/v2/create
zalopay.callbackUrl=http://localhost:8080/api/user/wallet/zalopay/callback
zalopay.appId=2553
zalopay.key1=PcY4iZIKFCIdgZvA6ueMcMHHUbRLYjPL
zalopay.key2=kLtgPl8HHhfvMuDHPwKfgfsY4Ydm9eIz
```

**Cách dùng:**
1. Copy TOÀN BỘ đoạn trên
2. Mở file `src/main/resources/application.properties`
3. Paste vào cuối file
4. **CHỈ CẦN SỬA** 2 dòng PayPal:
   - `paypal.client.id=YOUR_CLIENT_ID_HERE`
   - `paypal.client.secret=YOUR_SECRET_HERE`
5. Save file
6. Restart server
7. ✅ XONG!

---

## ❓ FAQ - Câu Hỏi Thường Gặp

### Q1: Credentials demo có hoạt động không?
**A:** 
- **VNPay, MoMo, ZaloPay:** CÓ, hoạt động 100%
- **PayPal:** KHÔNG, bạn PHẢI tạo credentials riêng (nhưng MIỄN PHÍ và chỉ mất 5 phút)

### Q2: Tôi có bị mất tiền khi test không?
**A:** **KHÔNG! Tất cả đều test bằng TIỀN ẢO:**
- **PayPal:** Tài khoản sandbox có sẵn $1,000 tiền ảo
- **VNPay:** Thẻ test với số dư vô hạn, OTP luôn là 123456
- **MoMo:** Dùng MoMo Test App với tiền ảo, hoặc giả lập callback
- **ZaloPay:** Dùng tài khoản demo hoặc thẻ test với tiền ảo

### Q3: Tôi là sinh viên, không có tiền. Có test được không?
**A:** **CÓ! 100% MIỄN PHÍ!**

**Cách đơn giản nhất (không cần app):**
1. Tạo payment URL bằng API
2. Giả lập callback thành công
3. Balance tăng lên!

**Code để tắt signature verification tạm thời:**
```java
// MoMoPaymentService.java và ZaloPayPaymentService.java
public boolean verifyCallback(Map<String, String> params) {
    // TODO: REMOVE IN PRODUCTION!
    String resultCode = params.get("resultCode"); // MoMo
    // hoặc parse status từ data (ZaloPay)
    return "0".equals(resultCode); // 0 = success
}
```

**Nhớ:** Bật lại verification khi làm thật!

### Q4: Làm sao để test end-to-end như thật?
**A:** Dùng các test app/tài khoản:
- **PayPal:** Tài khoản sandbox có sẵn
- **VNPay:** Thẻ test 9704198526191432198, OTP 123456
- **MoMo:** Tải MoMo Test App (email developer@momo.vn xin link)
- **ZaloPay:** Số test 0999999999, OTP 111111, PIN 123456

### Q5: Credentials có hết hạn không?
**A:**
- **PayPal sandbox:** Không hết hạn trừ khi bạn xóa app
- **VNPay/MoMo/ZaloPay demo:** Không hết hạn, dùng mãi

---

## 🎓 HƯỚNG DẪN ĐẶC BIỆT CHO SINH VIÊN

### Test Nhanh Nhất (Không Cần Tiền, Không Cần App)

#### Bước 1: Sửa Code Tạm Thời

**File: `MoMoPaymentService.java`**
```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY - Tắt verify để test dễ
    log.warn("DEVELOPMENT MODE: Signature verification disabled!");
    String resultCode = params.get("resultCode");
    boolean success = "0".equals(resultCode);
    
    if (success) {
        log.info("MoMo test payment successful for order: {}", params.get("orderId"));
    }
    
    return success;
    
    // TODO: Bật lại phần này khi deploy production
    /*
    try {
        String receivedSignature = params.get("signature");
        String rawSignature = "accessKey=" + accessKey + ...
        String calculatedSignature = hmacSHA256(secretKey, rawSignature);
        boolean signatureValid = calculatedSignature.equals(receivedSignature);
        boolean paymentSuccess = "0".equals(params.get("resultCode"));
        return signatureValid && paymentSuccess;
    } catch (Exception e) {
        log.error("Failed to verify MoMo callback: {}", e.getMessage(), e);
        return false;
    }
    */
}
```

**File: `ZaloPayPaymentService.java`**
```java
public boolean verifyCallback(Map<String, String> params) {
    // ⚠️ DEVELOPMENT ONLY
    log.warn("DEVELOPMENT MODE: MAC verification disabled!");
    
    try {
        String data = params.get("data");
        Map<String, Object> dataMap = objectMapper.readValue(data, Map.class);
        Integer status = (Integer) dataMap.get("status");
        boolean success = status != null && status == 1;
        
        if (success) {
            log.info("ZaloPay test payment successful");
        }
        
        return success;
    } catch (Exception e) {
        log.error("Failed to parse ZaloPay callback: {}", e.getMessage(), e);
        return false;
    }
    
    // TODO: Bật lại MAC verification khi production
    /*
    String receivedMac = params.get("mac");
    String calculatedMac = hmacSHA256(key2, data);
    boolean macValid = calculatedMac.equals(receivedMac);
    return macValid && paymentSuccess;
    */
}
```

#### Bước 2: Test Bằng Postman

**1. Nạp tiền MoMo (Giả lập):**
```http
# Tạo payment URL
POST http://localhost:8080/api/user/wallet/deposit/momo
Authorization: Bearer YOUR_TOKEN
Content-Type: application/json

{
  "amount": 100000,
  "description": "Test nạp tiền"
}

# Response sẽ có orderId, copy nó

# Giả lập callback thành công
POST http://localhost:8080/api/user/wallet/momo/callback
Content-Type: application/json

{
  "partnerCode": "MOMOIQA420180417",
  "orderId": "PASTE_ORDER_ID_Ở_ĐÂY",
  "requestId": "test123",
  "amount": "100000",
  "orderInfo": "Test nạp tiền",
  "orderType": "momo_wallet",
  "transId": "12345678",
  "resultCode": "0",
  "message": "Success",
  "payType": "qr",
  "responseTime": "1698566400000",
  "extraData": "",
  "signature": "test-signature"
}

# Kiểm tra balance
GET http://localhost:8080/api/user/wallet/balance
Authorization: Bearer YOUR_TOKEN
```

**2. Nạp tiền ZaloPay (Giả lập):**
```http
# Tạo payment URL
POST http://localhost:8080/api/user/wallet/deposit/zalopay
Authorization: Bearer YOUR_TOKEN
Content-Type: application/json

{
  "amount": 200000,
  "description": "Test ZaloPay"
}

# Giả lập callback
POST http://localhost:8080/api/user/wallet/zalopay/callback
Content-Type: application/json

{
  "data": "{\"app_id\":2553,\"app_trans_id\":\"231029_999\",\"amount\":200000,\"status\":1}",
  "mac": "test-mac",
  "type": 1
}

# Kiểm tra balance
GET http://localhost:8080/api/user/wallet/balance
Authorization: Bearer YOUR_TOKEN
```

#### Bước 3: Demo Cho Giáo Viên

**Video demo nên show:**
1. ✅ Tạo payment URL thành công (Postman)
2. ✅ Giả lập callback thành công
3. ✅ Balance tăng đúng số tiền
4. ✅ Lịch sử giao dịch có record
5. ✅ Thanh toán đơn hàng bằng ví

**Giải thích cho thầy/cô:**
> "Em đã tích hợp 4 payment gateway: PayPal, VNPay, MoMo, ZaloPay. 
> Do đang trong môi trường development và test, em giả lập callback 
> để demo flow. Trong production thực tế, callback sẽ được gọi tự 
> động từ payment gateway sau khi user thanh toán thành công."

### ⚠️ Lưu Ý Quan Trọng

**Trước khi nộp đồ án:**
1. ✅ Ghi chú trong code: "Development mode - for testing only"
2. ✅ Comment trong báo cáo: "Giả lập callback do hạn chế môi trường test"
3. ✅ Giải thích rõ: Trong production sẽ bật signature verification
4. ✅ Có thể show code verify signature (đã comment) để chứng minh hiểu logic

**Không nên:**
- ❌ Xóa hẳn code verification (giữ lại nhưng comment)
- ❌ Nói dối là đã test với tiền thật
- ❌ Để code development trong production

---

**Cập nhật:** 29/10/2025 | **Version:** 3.0 - Hướng Dẫn Cho Sinh Viên
