# Hướng Dẫn Fix Lỗi MOMO Callback Không Nhận Được (Localhost)

## Vấn Đề
Bạn đã thanh toán thành công trên App MOMO nhưng tiền không được cộng vào ví vì:
- **MOMO không thể gọi callback về localhost của bạn**
- Server của bạn đang chạy trên `http://localhost:8080` (không public)
- MOMO cần một URL public để gọi IPN/callback

## Giải Pháp: Dùng Ngrok

### Bước 1: Download và cài Ngrok
1. Truy cập: https://ngrok.com/download
2. Download bản Windows
3. Giải nén file `ngrok.exe` vào thư mục bất kỳ (ví dụ: `C:\ngrok`)
4. (Optional) Đăng ký tài khoản ngrok để lấy auth token

### Bước 2: Chạy Ngrok
Mở Command Prompt hoặc PowerShell:
```bash
# Di chuyển đến thư mục chứa ngrok.exe
cd C:\ngrok

# Chạy ngrok
ngrok http 8080
```

Hoặc nếu đã add ngrok vào PATH:
```bash
ngrok http 8080
```

### Bước 3: Copy URL từ Ngrok
Bạn sẽ thấy màn hình như này:
```
ngrok

Session Status                online
Account                       Your Account (Plan: Free)
Version                       3.x.x
Region                        Asia Pacific (ap)
Latency                       -
Web Interface                 http://127.0.0.1:4040
Forwarding                    https://abc123-def456.ngrok-free.app -> http://localhost:8080

Connections                   ttl     opn     rt1     rt5     p50     p90
                              0       0       0.00    0.00    0.00    0.00
```

**Copy URL này**: `https://abc123-def456.ngrok-free.app` ← Đây là URL public của bạn!

### Bước 4: Cập nhật application.properties
Mở file `src/main/resources/application.properties` và thay đổi:

**TỪ:**
```properties
momo.notify-url=http://localhost:8080/api/public/momo/ipn
momo.return-url=http://localhost:8080/api/public/momo/callback
```

**SANG:**
```properties
momo.notify-url=https://abc123-def456.ngrok-free.app/api/public/momo/ipn
momo.return-url=https://abc123-def456.ngrok-free.app/api/public/momo/callback
```

⚠️ **QUAN TRỌNG**: Thay `abc123-def456.ngrok-free.app` bằng URL ngrok của BẠN!

### Bước 5: Restart ứng dụng Spring Boot
- Stop server hiện tại (Ctrl+C trong terminal chạy Spring Boot)
- Start lại server

### Bước 6: Test lại
Bây giờ test lại:

1. **Gọi API tạo link thanh toán**:
```bash
POST /api/user/wallet/deposit/momo
{
  "amount": 50000,
  "description": "Test nạp tiền",
  "paymentMethod": "app"
}
```

2. **Mở payUrl trên điện thoại và thanh toán**

3. **Kiểm tra logs** - Bạn sẽ thấy:
```
INFO  Handling MOMO callback for orderId: DEPOSIT_4_...
INFO  Result code: 0, Message: Successful.
INFO  Deposit successful for user 4. Balance: 0.00 -> 50000.00
```

4. **Kiểm tra ví**:
```bash
GET /api/user/wallet
```

Bạn sẽ thấy tiền đã được cộng vào!

---

## Kiểm Tra Giao Dịch Cũ Đã Thanh toán

Nếu bạn đã thanh toán nhưng chưa nhận được tiền, bạn có thể:

### Option 1: Thanh toán lại (Khuyên dùng)
- Tạo giao dịch mới sau khi đã setup ngrok
- Thanh toán lại và kiểm tra

### Option 2: Cộng tiền thủ công cho giao dịch cũ
Nếu bạn đã thanh toán thành công và có `transId` từ MOMO:

**Kiểm tra database**:
```sql
SELECT * FROM momo_transactions WHERE user_id = 4 ORDER BY created_at DESC;
```

**Nếu thấy giao dịch với status = 'pending'**, bạn có thể:

1. Lấy `orderId` từ database
2. Gọi API callback thủ công (giả lập MOMO callback):

```bash
GET http://localhost:8080/api/public/momo/callback?partnerCode=MOMO&orderId=DEPOSIT_4_1234567890&requestId=abc-123&amount=50000&orderInfo=Nạp+tiền+vào+ví&resultCode=0&message=Successful&transId=123456789&payType=qr&responseTime=1698123456789&extraData=&signature=dummy
```

⚠️ **CHÚ Ý**: Chỉ làm cách này khi bạn CHẮC CHẮN đã thanh toán thành công trên App MOMO!

---

## Lưu Ý Quan Trọng

### 1. Ngrok URL thay đổi mỗi lần restart
- Nếu bạn stop ngrok và start lại, URL sẽ thay đổi
- Bạn phải cập nhật lại `application.properties`
- **Giải pháp**: Dùng ngrok paid để có fixed domain

### 2. Kiểm tra Ngrok Web Interface
Mở browser và truy cập: http://127.0.0.1:4040

Đây là dashboard của ngrok, bạn có thể:
- Xem tất cả requests đến server
- Kiểm tra xem MOMO có gọi IPN/callback không
- Debug requests

### 3. Test với Postman/curl trước
Trước khi test với MOMO thật, test ngrok hoạt động chưa:

```bash
# Test từ máy khác hoặc điện thoại
curl https://abc123-def456.ngrok-free.app/api/public/momo/callback

# Nếu nhận được response (không phải lỗi 404/502) là OK!
```

### 4. Firewall/Antivirus
- Đảm bảo ngrok không bị firewall/antivirus chặn
- Cho phép ngrok kết nối internet

---

## Troubleshooting

### Lỗi: "ngrok not found"
- Đảm bảo đã download và giải nén ngrok.exe
- CD đến đúng thư mục chứa ngrok.exe
- Hoặc add ngrok vào PATH

### Lỗi: "Failed to start tunnel"
- Kiểm tra port 8080 đã được dùng chưa
- Đảm bảo Spring Boot đang chạy trên port 8080
- Thử restart ngrok

### Lỗi: MOMO vẫn không gọi callback
1. Kiểm tra `momo.notify-url` trong `application.properties` đã đúng chưa
2. Kiểm tra Spring Boot đã restart chưa
3. Xem ngrok dashboard (http://127.0.0.1:4040) có request từ MOMO không
4. Kiểm tra logs Spring Boot

### Tiền vẫn chưa vào
1. Kiểm tra logs có dòng "Handling MOMO callback..." không
2. Kiểm tra `resultCode` có = 0 không
3. Kiểm tra database:
```sql
SELECT * FROM momo_transactions WHERE user_id = 4;
SELECT * FROM wallet_transactions WHERE user_id = 4;
SELECT wallet_balance FROM users WHERE id = 4;
```

---

## Production Deployment

Khi deploy lên production (VPS, Cloud):
1. ❌ **KHÔNG dùng ngrok**
2. ✅ Dùng domain thật với HTTPS
3. ✅ Cập nhật `momo.notify-url` và `momo.return-url` với domain thật
4. ✅ Đăng ký MOMO production credentials (không dùng UAT)

---

## Tóm Tắt

1. ✅ Download và cài ngrok
2. ✅ Chạy: `ngrok http 8080`
3. ✅ Copy URL ngrok (vd: `https://abc123.ngrok-free.app`)
4. ✅ Cập nhật `application.properties`:
   - `momo.notify-url=https://abc123.ngrok-free.app/api/public/momo/ipn`
   - `momo.return-url=https://abc123.ngrok-free.app/api/public/momo/callback`
5. ✅ Restart Spring Boot
6. ✅ Test lại thanh toán
7. ✅ Kiểm tra logs có "Handling MOMO callback..."
8. ✅ Kiểm tra tiền đã vào ví

**Xong! Bây giờ MOMO sẽ gọi được callback về server của bạn! 🎉**

