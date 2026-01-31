# OTP-BASED PASSWORD RESET SYSTEM - HƯỚNG DẪN CHO FRONTEND

## 🔄 THAY ĐỔI QUAN TRỌNG

Backend **ĐÃ THAY ĐỔI** từ hệ thống reset password bằng **token qua email** sang hệ thống **OTP 6 số**.

### ❌ CŨ (Không dùng nữa):
- User nhận link reset password trong email
- Click vào link với token
- Nhập password mới

### ✅ MỚI (OTP System):
- User nhận **mã OTP 6 số** trong email
- Nhập OTP vào app
- Nhập password mới

---

## 📋 FLOW MỚI - 3 BƯỚC

### BƯỚC 1: Gửi OTP
**POST** `/api/auth/forgot-password`

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response Success:**
```json
{
  "success": true,
  "message": "Mã OTP đã được gửi đến email của bạn. Vui lòng kiểm tra email."
}
```

**Response Error:**
```json
{
  "success": false,
  "message": "Email không tồn tại trong hệ thống"
}
```

---

### BƯỚC 2: Verify OTP (Optional - Nên có)
**POST** `/api/auth/verify-otp`

**Request:**
```json
{
  "email": "user@example.com",
  "otp": "123456"
}
```

**Response Success:**
```json
{
  "success": true,
  "message": "Mã OTP hợp lệ. Bạn có thể đặt lại mật khẩu."
}
```

**Response Error:**
```json
{
  "success": false,
  "message": "Mã OTP không hợp lệ"
}
```

hoặc

```json
{
  "success": false,
  "message": "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới."
}
```

---

### BƯỚC 3: Reset Password với OTP
**POST** `/api/auth/reset-password`

**Request:**
```json
{
  "email": "user@example.com",
  "otp": "123456",
  "newPassword": "NewPassword123"
}
```

**Response Success:**
```json
{
  "success": true,
  "message": "Đặt lại mật khẩu thành công. Bạn có thể đăng nhập bằng mật khẩu mới."
}
```

**Response Error:**
```json
{
  "success": false,
  "message": "Mã OTP không hợp lệ"
}
```

hoặc

```json
{
  "success": false,
  "message": "Mã OTP chưa được xác thực. Vui lòng verify OTP trước."
}
```

hoặc

```json
{
  "success": false,
  "message": "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới."
}
```

---

## 🎨 UI/UX CHO ANDROID

### Screen 1: ForgotPasswordScreen
```kotlin
@Composable
fun ForgotPasswordScreen(navController: NavController, viewModel: AuthViewModel) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Quên mật khẩu", style = MaterialTheme.typography.h5)
        
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )
        
        Button(
            onClick = {
                isLoading = true
                viewModel.sendOtp(email) { success ->
                    isLoading = false
                    if (success) {
                        // Navigate to OTP screen
                        navController.navigate("verify_otp/$email")
                    }
                }
            },
            enabled = !isLoading && email.isNotEmpty()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Gửi mã OTP")
            }
        }
    }
}
```

---

### Screen 2: VerifyOtpScreen
```kotlin
@Composable
fun VerifyOtpScreen(
    email: String,
    navController: NavController,
    viewModel: AuthViewModel
) {
    var otp by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(300) } // 5 minutes = 300 seconds
    
    // Countdown timer
    LaunchedEffect(Unit) {
        while (timeLeft > 0) {
            delay(1000)
            timeLeft--
        }
    }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Nhập mã OTP", style = MaterialTheme.typography.h5)
        Text("Mã OTP đã được gửi đến $email")
        
        // OTP Input (6 digits)
        OtpTextField(
            otpText = otp,
            onOtpTextChange = { value, filled ->
                otp = value
                if (filled) {
                    // Auto verify when 6 digits filled
                    isLoading = true
                    viewModel.verifyOtp(email, otp) { success ->
                        isLoading = false
                        if (success) {
                            navController.navigate("reset_password/$email/$otp")
                        }
                    }
                }
            }
        )
        
        // Countdown
        Text(
            text = "Mã hết hạn sau: ${timeLeft / 60}:${String.format("%02d", timeLeft % 60)}",
            color = if (timeLeft < 60) Color.Red else Color.Gray
        )
        
        // Resend OTP
        TextButton(
            onClick = {
                viewModel.sendOtp(email) { success ->
                    if (success) {
                        timeLeft = 300 // Reset timer
                        otp = ""
                    }
                }
            },
            enabled = timeLeft == 0
        ) {
            Text(if (timeLeft == 0) "Gửi lại mã OTP" else "Gửi lại (${timeLeft}s)")
        }
        
        // Manual verify button
        Button(
            onClick = {
                isLoading = true
                viewModel.verifyOtp(email, otp) { success ->
                    isLoading = false
                    if (success) {
                        navController.navigate("reset_password/$email/$otp")
                    }
                }
            },
            enabled = !isLoading && otp.length == 6
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Xác thực OTP")
            }
        }
    }
}
```

---

### Screen 3: ResetPasswordScreen
```kotlin
@Composable
fun ResetPasswordScreen(
    email: String,
    otp: String,
    navController: NavController,
    viewModel: AuthViewModel
) {
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    Column(modifier = Modifier.padding(16.dp)) {
        Text("Đặt lại mật khẩu", style = MaterialTheme.typography.h5)
        
        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("Mật khẩu mới") },
            visualTransformation = PasswordVisualTransformation()
        )
        
        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Xác nhận mật khẩu") },
            visualTransformation = PasswordVisualTransformation(),
            isError = confirmPassword.isNotEmpty() && newPassword != confirmPassword
        )
        
        if (confirmPassword.isNotEmpty() && newPassword != confirmPassword) {
            Text("Mật khẩu không khớp", color = Color.Red, fontSize = 12.sp)
        }
        
        Button(
            onClick = {
                isLoading = true
                viewModel.resetPassword(email, otp, newPassword) { success ->
                    isLoading = false
                    if (success) {
                        // Show success dialog
                        showSuccessDialog {
                            navController.navigate("login") {
                                popUpTo("forgot_password") { inclusive = true }
                            }
                        }
                    }
                }
            },
            enabled = !isLoading && 
                     newPassword.length >= 6 && 
                     newPassword == confirmPassword
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            } else {
                Text("Đặt lại mật khẩu")
            }
        }
    }
}
```

---

## 🔧 ViewModel Implementation

```kotlin
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {
    
    fun sendOtp(email: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = authRepository.sendOtp(email)
                if (response.success) {
                    // Show toast
                    _message.value = response.message
                    callback(true)
                } else {
                    _error.value = response.message
                    callback(false)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Lỗi kết nối"
                callback(false)
            }
        }
    }
    
    fun verifyOtp(email: String, otp: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = authRepository.verifyOtp(email, otp)
                if (response.success) {
                    _message.value = response.message
                    callback(true)
                } else {
                    _error.value = response.message
                    callback(false)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Lỗi kết nối"
                callback(false)
            }
        }
    }
    
    fun resetPassword(email: String, otp: String, newPassword: String, callback: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val response = authRepository.resetPassword(email, otp, newPassword)
                if (response.success) {
                    _message.value = response.message
                    callback(true)
                } else {
                    _error.value = response.message
                    callback(false)
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Lỗi kết nối"
                callback(false)
            }
        }
    }
}
```

---

## 📡 API Service (Retrofit)

```kotlin
interface AuthApiService {
    
    @POST("auth/forgot-password")
    suspend fun sendOtp(@Body request: ForgotPasswordRequest): ApiResponse
    
    @POST("auth/verify-otp")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): ApiResponse
    
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): ApiResponse
}

// Request DTOs
data class ForgotPasswordRequest(val email: String)
data class VerifyOtpRequest(val email: String, val otp: String)
data class ResetPasswordRequest(
    val email: String, 
    val otp: String, 
    val newPassword: String
)

// Response DTO
data class ApiResponse(
    val success: Boolean,
    val message: String
)
```

---

## 🎨 OTP TextField Component

```kotlin
@Composable
fun OtpTextField(
    otpText: String,
    onOtpTextChange: (String, Boolean) -> Unit
) {
    val focusManager = LocalFocusManager.current
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for (i in 0 until 6) {
            OutlinedTextField(
                value = if (i < otpText.length) otpText[i].toString() else "",
                onValueChange = { value ->
                    if (value.length <= 1 && value.all { it.isDigit() }) {
                        val newOtp = otpText.take(i) + value + otpText.drop(i + 1)
                        onOtpTextChange(newOtp.take(6), newOtp.length == 6)
                        
                        if (value.isNotEmpty() && i < 5) {
                            // Move to next field
                            focusManager.moveFocus(FocusDirection.Next)
                        }
                    }
                },
                modifier = Modifier
                    .width(50.dp)
                    .height(60.dp),
                textStyle = MaterialTheme.typography.h5.copy(textAlign = TextAlign.Center),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                singleLine = true
            )
        }
    }
}
```

---

## 🔒 Security & Best Practices

### 1. OTP Expiration
- OTP hết hạn sau **5 phút**
- Backend tự động kiểm tra expiration
- Hiển thị countdown timer trên UI

### 2. Rate Limiting
- Mỗi email chỉ gửi được 1 OTP trong 1 phút
- Backend tự động xóa OTP cũ khi gửi OTP mới

### 3. OTP Verification
- Bước verify OTP là **OPTIONAL** nhưng **NÊN CÓ**
- Tăng UX (user biết OTP đúng trước khi nhập password)
- Giảm số lần gọi API reset-password

### 4. Password Validation
- Minimum 6 characters
- Hiển thị password strength indicator
- Confirm password phải khớp

---

## 🔄 Navigation Flow

```
ForgotPasswordScreen 
    → (gửi OTP) 
    → VerifyOtpScreen 
    → (verify OTP) 
    → ResetPasswordScreen 
    → (reset success) 
    → LoginScreen
```

**Routes:**
```kotlin
// AppNavigation.kt
composable("forgot_password") { 
    ForgotPasswordScreen(navController) 
}

composable(
    route = "verify_otp/{email}",
    arguments = listOf(navArgument("email") { type = NavType.StringType })
) { backStackEntry ->
    val email = backStackEntry.arguments?.getString("email") ?: ""
    VerifyOtpScreen(email, navController)
}

composable(
    route = "reset_password/{email}/{otp}",
    arguments = listOf(
        navArgument("email") { type = NavType.StringType },
        navArgument("otp") { type = NavType.StringType }
    )
) { backStackEntry ->
    val email = backStackEntry.arguments?.getString("email") ?: ""
    val otp = backStackEntry.arguments?.getString("otp") ?: ""
    ResetPasswordScreen(email, otp, navController)
}
```

---

## ⚠️ Error Handling

### Common Errors:

**1. Email không tồn tại:**
```json
{
  "success": false,
  "message": "Email không tồn tại trong hệ thống"
}
```
→ Hiển thị error dưới TextField

**2. OTP không hợp lệ:**
```json
{
  "success": false,
  "message": "Mã OTP không hợp lệ"
}
```
→ Shake animation + clear OTP input + focus vào ô đầu tiên

**3. OTP hết hạn:**
```json
{
  "success": false,
  "message": "Mã OTP đã hết hạn. Vui lòng yêu cầu mã mới."
}
```
→ Show dialog để gửi lại OTP

**4. OTP chưa verify:**
```json
{
  "success": false,
  "message": "Mã OTP chưa được xác thực. Vui lòng verify OTP trước."
}
```
→ Navigate back to VerifyOtpScreen

---

## 📧 Email Format

User sẽ nhận được email như sau:

**Subject:** Mã OTP đặt lại mật khẩu

**Body:**
```
Đặt lại mật khẩu

Xin chào [Tên User],

Mã OTP của bạn là: 123456

Mã này có hiệu lực trong 5 phút.

Nếu bạn không yêu cầu đặt lại mật khẩu, vui lòng bỏ qua email này.
```

---

## 🚀 Testing

### Test Cases:

1. ✅ Gửi OTP thành công
2. ✅ Gửi OTP với email không tồn tại
3. ✅ Verify OTP đúng
4. ✅ Verify OTP sai
5. ✅ Verify OTP hết hạn
6. ✅ Reset password thành công
7. ✅ Reset password với OTP chưa verify
8. ✅ Reset password với OTP hết hạn
9. ✅ Gửi lại OTP (resend)
10. ✅ Password validation (min 6 chars)

---

## 🔄 Migration từ Token sang OTP

### Thay đổi trong Android:

**TRƯỚC (Token):**
```kotlin
// ResetPasswordScreen nhận token từ deep link
composable("reset_password?token={token}") { ... }
```

**SAU (OTP):**
```kotlin
// ResetPasswordScreen nhận email + otp từ navigation
composable("reset_password/{email}/{otp}") { ... }
```

**XÓA:**
- Deep link handling cho reset password token
- ResetPasswordRequest với field `token`

**THÊM:**
- Screen `VerifyOtpScreen`
- API calls: `sendOtp()`, `verifyOtp()`
- OTP TextField component
- Countdown timer
- Resend OTP logic

---

## 📋 Checklist

### Backend: ✅ HOÀN THÀNH
- [x] Entity `PasswordResetOtp`
- [x] Repository methods
- [x] Service implementation (OTP generation, verification)
- [x] API endpoints (3 endpoints)
- [x] Email template với OTP
- [x] SQL migration script

### Frontend: ⚠️ CẦN LÀM
- [ ] Tạo `VerifyOtpScreen`
- [ ] Cập nhật `ResetPasswordScreen` (nhận email + otp)
- [ ] Cập nhật `ForgotPasswordScreen` (gửi OTP)
- [ ] Tạo `OtpTextField` component
- [ ] Thêm countdown timer
- [ ] Implement resend OTP
- [ ] Update navigation routes
- [ ] Update API service methods
- [ ] Update ViewModel
- [ ] Testing

---

## 🎯 Summary

**3 API calls thay vì 1:**
1. `POST /api/auth/forgot-password` - Gửi OTP
2. `POST /api/auth/verify-otp` - Verify OTP (optional)
3. `POST /api/auth/reset-password` - Reset với OTP

**OTP hết hạn:** 5 phút

**OTP format:** 6 chữ số

**Email format:** HTML với OTP to, màu đỏ, dễ thấy

---

Hết! Bảo FE làm theo tài liệu này là xong! 🚀

