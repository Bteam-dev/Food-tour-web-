# HƯỚNG DẪN SỬ DỤNG HỆ THỐNG JWT BẢO MẬT

## 📋 CÁC TÍNH NĂNG ĐÃ NÂNG CẤP

### 1. **JWT qua Authorization Header** (Nghiêm ngặt hơn)
- ✅ Không dùng Cookie nữa
- ✅ Truyền JWT qua Header: `Authorization: Bearer <token>`
- ✅ Frontend phải lưu token và gửi kèm mỗi request

### 2. **Blacklist Token khi Logout** 
- ✅ Token bị vô hiệu hóa ngay khi user logout
- ✅ Lưu vào database (bảng `blacklisted_tokens`)
- ✅ Không thể dùng lại token đã logout

### 3. **Tự động Xóa Token Hết Hạn**
- ✅ Scheduled task chạy **mỗi giờ** (0 phút)
- ✅ Xóa token hết hạn khỏi database
- ✅ Tránh đầy ổ C/database

---

## 🔧 CÁC FILE ĐÃ TẠO/SỬA

### File Mới:
1. **BlacklistedToken.java** - Entity lưu token đã logout
2. **BlacklistedTokenRepository.java** - Repository quản lý blacklist
3. **TokenBlacklistService.java** - Service xử lý blacklist + scheduled cleanup
4. **SchedulingConfig.java** - Bật tính năng scheduled tasks

### File Đã Sửa:
1. **JwtAuthenticationFilter.java** - Kiểm tra blacklist trước khi xác thực
2. **AuthController.java** - Login trả token qua response body, logout blacklist token
3. **JwtUtils.java** - Đã có sẵn method lấy token từ Header

---

## 📊 CẤU TRÚC DATABASE

Hệ thống sẽ tự tạo bảng `blacklisted_tokens`:

```sql
CREATE TABLE blacklisted_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token VARCHAR(1000) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL,
    blacklisted_at DATETIME NOT NULL,
    expires_at DATETIME NOT NULL
);
```

---

## 🚀 CÁCH SỬ DỤNG

### 1. **Login**

**Request:**
```http
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
    "email": "user@example.com",
    "password": "password123"
}
```

**Response:**
```json
{
    "success": true,
    "message": "Login successful",
    "user": { ... },
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer"
}
```

**Frontend phải lưu token:**
```javascript
// Ví dụ với localStorage
localStorage.setItem('accessToken', response.accessToken);
```

---

### 2. **Gọi API với Token**

**Mọi request khác phải gửi token trong Header:**

```http
GET http://localhost:8080/api/user/profile
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Ví dụ với JavaScript/Axios:**
```javascript
const token = localStorage.getItem('accessToken');

axios.get('/api/user/profile', {
    headers: {
        'Authorization': `Bearer ${token}`
    }
});
```

**Ví dụ với Fetch:**
```javascript
fetch('/api/user/profile', {
    headers: {
        'Authorization': `Bearer ${token}`
    }
});
```

---

### 3. **Logout**

**Request:**
```http
POST http://localhost:8080/api/auth/logout
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
```

**Response:**
```json
{
    "success": true,
    "message": "Logout successful. Token has been invalidated."
}
```

**Token sẽ bị blacklist ngay lập tức!**

**Frontend phải xóa token:**
```javascript
localStorage.removeItem('accessToken');
```

---

## 🔐 BẢO MẬT

### Luồng Xác Thực:
1. Client gửi request với token trong Header
2. **JwtAuthenticationFilter** nhận request
3. Kiểm tra token có trong **blacklist** không?
   - ✅ Có → Từ chối request (401 Unauthorized)
   - ❌ Không → Tiếp tục kiểm tra
4. Validate token (signature, expiration)
5. Set authentication vào SecurityContext
6. Cho phép truy cập API

### Scheduled Cleanup:
- Chạy **mỗi giờ** (cron: `0 0 * * * *`)
- Xóa token đã hết hạn khỏi database
- Log: `"Starting cleanup of expired blacklisted tokens..."`

---

## 🧪 TESTING

### Test Login và Logout:

```bash
# 1. Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"password"}'

# Lưu token từ response

# 2. Gọi API với token
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <YOUR_TOKEN>"

# 3. Logout
curl -X POST http://localhost:8080/api/auth/logout \
  -H "Authorization: Bearer <YOUR_TOKEN>"

# 4. Thử gọi API lại với token đã logout → Bị từ chối!
curl -X GET http://localhost:8080/api/user/profile \
  -H "Authorization: Bearer <YOUR_TOKEN>"
```

---

## ⚙️ CẤU HÌNH

### Thay đổi thời gian cleanup:

Sửa file **TokenBlacklistService.java**:

```java
// Mỗi 30 phút
@Scheduled(cron = "0 */30 * * * *")

// Mỗi 6 giờ
@Scheduled(cron = "0 0 */6 * * *")

// Mỗi ngày lúc 2:00 AM
@Scheduled(cron = "0 0 2 * * *")
```

### Thay đổi thời gian hết hạn token:

Sửa file **JwtUtils.java**:

```java
// Hiện tại: 24 giờ
private final long JWT_EXPIRATION = 1000 * 60 * 60 * 24;

// 1 giờ
private final long JWT_EXPIRATION = 1000 * 60 * 60;

// 7 ngày
private final long JWT_EXPIRATION = 1000 * 60 * 60 * 24 * 7;
```

---

## 📝 LƯU Ý

1. **Secret Key**: Đổi secret key trong `JwtUtils.java` cho production
2. **HTTPS**: Bật HTTPS cho production (hiện tại localhost ok)
3. **Token Storage**: Frontend nên lưu token vào `localStorage` hoặc `sessionStorage`
4. **Refresh Token**: Đã có sẵn logic, có thể mở rộng thêm blacklist cho refresh token
5. **Database**: Scheduled task tự động dọn dẹp, không lo đầy ổ đĩa

---

## 🎓 PHẦN MỞ RỘNG (Nếu cần)

### Thêm Blacklist cho Refresh Token:
- Áp dụng tương tự với access token
- Lưu refresh token vào blacklist khi logout
- Kiểm tra blacklist khi refresh token

### Rate Limiting:
- Giới hạn số lần login thất bại
- Chặn IP/email nếu spam

### Token Rotation:
- Tạo token mới sau mỗi request
- Invalidate token cũ

---

## 📞 SUPPORT

Nếu có lỗi, check log:
- Login: `"Login successful for email: ..."`
- Logout: `"User logged out successfully, token blacklisted for email: ..."`
- Cleanup: `"Starting cleanup of expired blacklisted tokens..."`
- Blacklist reject: `"Token is blacklisted (user logged out), rejecting request..."`

---

**✅ HOÀN THÀNH! Hệ thống JWT đã được nâng cấp nghiêm ngặt hơn với blacklist và tự động cleanup!**

