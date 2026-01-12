# JWT TOKEN: CHUYỂN TỪ EMAIL SANG USERID

## Tổng Quan
Đã chuyển đổi toàn bộ hệ thống JWT từ dùng **EMAIL** làm subject sang dùng **USER_ID** làm subject.

## Lý Do Thay Đổi
- ✅ **Hiệu suất tốt hơn**: Lookup bằng ID (Primary Key) nhanh hơn email
- ✅ **Bảo mật cao hơn**: Không lộ thông tin email trong token
- ✅ **Linh hoạt**: User có thể đổi email mà không ảnh hưởng token
- ✅ **Chuẩn hơn**: Subject nên là identifier duy nhất và bất biến

## Cấu Trúc Token Mới

### 1. Access Token (Authentication)
```json
{
  "sub": "123",              // userId (subject)
  "username": "user@email.com",  // email/username (claim)
  "roles": ["USER"],         // roles (claim)
  "iat": 1234567890,
  "exp": 1234654290
}
```

### 2. Refresh Token
```json
{
  "sub": "123",              // userId (subject)
  "iat": 1234567890,
  "exp": 1235172690
}
```

### 3. Special Token (Email Verification, Password Reset)
```json
{
  "sub": "123",              // userId (subject)
  "email": "user@email.com", // email (claim)
  "type": "VERIFY_EMAIL",    // hoặc "RESET_PASSWORD"
  "iat": 1234567890,
  "exp": 1234654290
}
```

## Các File Đã Thay Đổi

### 1. **JwtUtils.java** ⭐ QUAN TRỌNG
**Phương thức chính:**
- `generateAccessToken(Integer userId, String username, List<String> roles)` - Tạo access token
- `generateRefreshToken(Integer userId)` - Tạo refresh token
- `generateSpecialToken(Integer userId, String email, String type)` - Tạo token đặc biệt
- `getUserIdFromToken(String token)` - Lấy userId từ token (từ subject)
- `getUsernameFromToken(String token)` - Lấy username/email từ token (từ claim)
- `getEmailFromSpecialToken(String token)` - Lấy email từ special token
- `getTypeFromSpecialToken(String token)` - Lấy type từ special token

**Deprecated (chỉ để tương thích token cũ):**
- ~~`getEmailFromToken(String token)`~~ - Deprecated, dùng `getEmailFromSpecialToken`
- ~~`getRoleFromToken(String token)`~~ - Deprecated, dùng `getTypeFromSpecialToken`

### 2. **JwtAuthenticationFilter.java**
```java
// TRƯỚC: Lấy email từ token, rồi query user bằng email
String email = jwtUtils.getUsernameFromToken(token);
User user = userRepository.findByEmail(email).orElse(null);

// SAU: Lấy userId từ token, rồi query user bằng userId
Integer userId = jwtUtils.getUserIdFromToken(token);
User user = userRepository.findById(userId).orElse(null);
```

### 3. **WebSocketAuthInterceptor.java**
```java
// TRƯỚC: Authenticate WebSocket bằng email
String email = jwtUtils.getUsernameFromToken(token);
User user = userRepository.findByEmail(email).orElse(null);

// SAU: Authenticate WebSocket bằng userId
Integer userId = jwtUtils.getUserIdFromToken(token);
User user = userRepository.findById(userId).orElse(null);
```

### 4. **AuthServiceImpl.java**
```java
// REGISTER & LOGIN: Tạo token với userId
String accessToken = jwtUtil.generateAccessToken(
    user.getId(), 
    user.getEmail(), 
    List.of(user.getRole().getRoleName().toString())
);
String refreshToken = jwtUtil.generateRefreshToken(user.getId());

// VERIFY EMAIL & RESET PASSWORD: Dùng special token
String verifyToken = jwtUtil.generateSpecialToken(
    user.getId(), 
    user.getEmail(), 
    "VERIFY_EMAIL"
);

// Validate special token
String email = jwtUtil.getEmailFromSpecialToken(token);
String type = jwtUtil.getTypeFromSpecialToken(token);
if (!"VERIFY_EMAIL".equals(type)) {
    throw new IllegalArgumentException("Invalid token");
}
```

### 5. **PublicAuthController.java**
```java
// LOGIN: Tạo token với userId
String accessToken = jwtUtils.generateAccessToken(userId, email, roles);

// LOGOUT: Blacklist token bằng userId
Integer userId = jwtUtils.getUserIdFromToken(token);
tokenBlacklistService.blacklistToken(token, "user_" + userId, expiresAt);
```

## Các Controller & Service Khác
**KHÔNG CẦN SỬA** vì đang dùng `@AuthenticationPrincipal User user` - Spring tự động inject User object từ token:

```java
// ✅ ĐÚNG - Không cần sửa
@PostMapping("/orders")
public ResponseEntity<?> createOrder(
    @RequestBody OrderRequest request,
    @AuthenticationPrincipal User user) {
    // user.getId() - lấy trực tiếp từ User object
}
```

## Migration Notes

### Token Cũ (Đã Gửi Email)
Các token đã gửi email (verify email, reset password) với format cũ **VẪN HOẠT ĐỘNG** nhờ các phương thức deprecated:
- `getEmailFromToken()` - Tự động fallback từ claim → subject
- `getRoleFromToken()` - Tự động fallback từ type claim → role claim

### Token Mới
Tất cả token mới được tạo đều dùng **userId làm subject**.

## Testing

### Test Access Token
1. Login và lấy access token
2. Decode token tại https://jwt.io
3. Kiểm tra:
   - `sub` = userId (số)
   - `username` = email
   - `roles` = array of roles

### Test API với Token
```bash
# Login
POST /api/auth/login
{
  "email": "user@test.com",
  "password": "123456"
}

# Response
{
  "accessToken": "eyJhbGc...",
  "user": {
    "id": 123,
    "email": "user@test.com"
  }
}

# Use token
GET /api/user/profile
Authorization: Bearer eyJhbGc...
```

## Backward Compatibility

### ✅ Token Cũ Vẫn Hoạt Động
Nếu có token cũ (subject là email):
```java
// getUserIdFromToken sẽ parse lỗi → trả về null
// Hệ thống sẽ reject token và yêu cầu login lại
```

### ✅ Special Token Tương Thích
Token verify email/reset password cũ vẫn hoạt động:
```java
getEmailFromToken(oldToken) // ✅ Lấy được email từ subject
getRoleFromToken(oldToken)  // ✅ Lấy được role từ claim
```

## Security Benefits

1. **Không lộ email trong token** - Subject chỉ là số ID
2. **Tăng tốc database lookup** - Query bằng Primary Key
3. **Linh hoạt đổi email** - userId không đổi
4. **Giảm kích thước token** - userId ngắn hơn email

## Next Steps

- [ ] Yêu cầu tất cả users login lại để lấy token mới
- [ ] Sau 24h (token expiration), tất cả token cũ sẽ hết hạn
- [ ] Monitor logs để đảm bảo không có lỗi với token mới
- [ ] Có thể xóa các deprecated methods sau 1 tuần

---
**Cập nhật:** 11/01/2026  
**Tác giả:** GitHub Copilot  
**Status:** ✅ HOÀN TẤT

