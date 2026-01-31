# ANDROID NAVIGATION - KIỂM TRA KHỚP VỚI BACKEND

## ✅ ĐÃ ĐÚNG:

### 1. Register Screen
```kotlin
composable(Screen.Register.route) {
    RegisterScreen(navController = navController)
}
```
✅ Đã xóa referral code logic - **KHỚP VỚI BACKEND**

### 2. OTP Verification Screen
```kotlin
composable(
    route = Screen.OTPVerification.route, // "otp_verification_screen/{email}"
    arguments = listOf(navArgument("email") { type = NavType.StringType })
) { backStackEntry ->
    val email = backStackEntry.arguments?.getString("email") ?: ""
    OTPVerificationScreen(navController, email = email)
}
```
✅ Đã có screen OTP mới - **KHỚP VỚI BACKEND**

### 3. Reset Password Screen
```kotlin
composable(
    route = Screen.PasswordReset.route, // "reset_password_screen/{email}/{otp}"
    arguments = listOf(
        navArgument("email") { type = NavType.StringType },
        navArgument("otp") { type = NavType.StringType }
    )
) { backStackEntry ->
    val email = backStackEntry.arguments?.getString("email") ?: ""
    val otp = backStackEntry.arguments?.getString("otp") ?: ""
    ResetPasswordScreen(navController, email = email, otp = otp)
}
```
✅ Đã nhận đúng email + otp - **KHỚP VỚI BACKEND**

### 4. Success Screen
```kotlin
composable(
    route = Screen.SuccessScreen.route, // "success_screen/{order_id}"
    arguments = listOf(navArgument("order_id") { type = NavType.IntType })
) { backStackEntry ->
    val orderId = backStackEntry.arguments?.getInt("order_id") ?: 0
    SuccessScreen(navController, orderId)
}
```
✅ Đã đổi sang IntType - **KHỚP VỚI BACKEND**

---

## ⚠️ CẦN KIỂM TRA:

### 1. SuccessScreen Component

**Cần kiểm tra:**
```kotlin
// File: presentation/components/SuccessScreen.kt
@Composable
fun SuccessScreen(navController: NavController, orderId: Int) {  // ⚠️ Phải là Int, không phải String
    // ...
}
```

**Nếu đang là String thì SỬA THÀNH:**
```kotlin
@Composable
fun SuccessScreen(navController: NavController, orderId: Int) {
    Column {
        Text("Đặt hàng thành công!")
        Text("Mã đơn hàng: #$orderId")
        // ...
    }
}
```

---

## 📝 FLOW ĐÚNG:

### Flow 1: Forgot Password (OTP)

```
ForgotPasswordScreen 
    ↓ (user nhập email, click "Gửi OTP")
    ↓ API: POST /api/auth/forgot-password
    ↓ Backend gửi OTP qua email
    ↓ Navigate: navController.navigate("otp_verification_screen/$email")
    ↓
OTPVerificationScreen (nhận email)
    ↓ (user nhập OTP 6 số)
    ↓ API: POST /api/auth/verify-otp
    ↓ Verify thành công
    ↓ Navigate: navController.navigate("reset_password_screen/$email/$otp")
    ↓
ResetPasswordScreen (nhận email + otp)
    ↓ (user nhập password mới)
    ↓ API: POST /api/auth/reset-password
    ↓ Reset thành công
    ↓ Navigate: navController.navigate("login_screen") { popUpTo("forgot_password_screen") { inclusive = true } }
    ↓
LoginScreen
```

### Flow 2: Create Order

```
CartScreen
    ↓ (user click "Đặt hàng")
    ↓ API: POST /api/user/orders
    ↓ Response: { "orderId": 123 }  // ⚠️ Integer type
    ↓ Navigate: navController.navigate("success_screen/$orderId")  // orderId là Int
    ↓
SuccessScreen (nhận orderId: Int)
```

---

## 🔧 CẦN SỬA Ở ANDROID:

### 1. ForgotPasswordScreen - Navigate đúng

**File:** `presentation/screens/auth/ForgotPasswordScreen.kt`

```kotlin
@Composable
fun ForgotPasswordScreen(navController: NavController, viewModel: AuthViewModel = hiltViewModel()) {
    var email by remember { mutableStateOf("") }
    
    Button(
        onClick = {
            viewModel.sendOtp(email) { success ->
                if (success) {
                    // ✅ ĐÚNG - Navigate với email
                    navController.navigate("otp_verification_screen/$email")
                }
            }
        }
    ) {
        Text("Gửi mã OTP")
    }
}
```

### 2. OTPVerificationScreen - Navigate đúng

**File:** `presentation/screens/auth/OTPVerificationScreen.kt`

```kotlin
@Composable
fun OTPVerificationScreen(
    navController: NavController,
    email: String,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var otp by remember { mutableStateOf("") }
    
    Button(
        onClick = {
            viewModel.verifyOtp(email, otp) { success ->
                if (success) {
                    // ✅ ĐÚNG - Navigate với email + otp
                    navController.navigate("reset_password_screen/$email/$otp")
                }
            }
        }
    ) {
        Text("Xác thực OTP")
    }
}
```

### 3. ResetPasswordScreen - Navigate đúng

**File:** `presentation/screens/auth/ResetPasswordScreen.kt`

```kotlin
@Composable
fun ResetPasswordScreen(
    navController: NavController,
    email: String,
    otp: String,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var newPassword by remember { mutableStateOf("") }
    
    Button(
        onClick = {
            viewModel.resetPassword(email, otp, newPassword) { success ->
                if (success) {
                    // ✅ ĐÚNG - Navigate về login và xóa back stack
                    navController.navigate("login_screen") {
                        popUpTo("forgot_password_screen") { inclusive = true }
                    }
                }
            }
        }
    ) {
        Text("Đặt lại mật khẩu")
    }
}
```

### 4. CartScreen - Navigate với orderId Int

**File:** `presentation/screens/cart/CartScreen.kt`

```kotlin
@Composable
fun CartScreen(navController: NavController, viewModel: CartViewModel = hiltViewModel()) {
    Button(
        onClick = {
            viewModel.createOrder { orderId ->
                // ✅ ĐÚNG - orderId là Int
                navController.navigate("success_screen/$orderId")
            }
        }
    ) {
        Text("Đặt hàng")
    }
}
```

### 5. SuccessScreen Component - Nhận Int

**File:** `presentation/components/SuccessScreen.kt`

```kotlin
@Composable
fun SuccessScreen(
    navController: NavController,
    orderId: Int  // ✅ ĐÚNG - Phải là Int, không phải String
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = Color.Green,
            modifier = Modifier.size(100.dp)
        )
        
        Text("Đặt hàng thành công!", style = MaterialTheme.typography.h5)
        Text("Mã đơn hàng: #$orderId", style = MaterialTheme.typography.body1)
        
        Button(
            onClick = {
                navController.navigate("userOrderScreen") {
                    popUpTo("home_screen") { inclusive = false }
                }
            }
        ) {
            Text("Xem đơn hàng")
        }
        
        TextButton(
            onClick = {
                navController.navigate("home_screen") {
                    popUpTo("home_screen") { inclusive = true }
                }
            }
        ) {
            Text("Về trang chủ")
        }
    }
}
```

---

## 🎯 KIỂM TRA CUỐI CÙNG:

### Checklist Android:

- [x] **Navigation routes đúng format** (có {email}, {otp}, {order_id})
- [x] **NavArgument types đúng** (StringType cho email/otp, IntType cho order_id)
- [x] **Parameter extraction đúng** (getString cho String, getInt cho Int)
- [ ] **SuccessScreen nhận Int** (CẦN KIỂM TRA!)
- [ ] **Navigate calls đúng** (navController.navigate("route/$param"))
- [ ] **API DTOs đúng** (email, otp, newPassword)

### Các API Backend tương ứng:

1. **POST /api/auth/forgot-password**
   ```json
   Request: { "email": "user@example.com" }
   Response: { "success": true, "message": "Mã OTP đã được gửi..." }
   ```

2. **POST /api/auth/verify-otp**
   ```json
   Request: { "email": "user@example.com", "otp": "123456" }
   Response: { "success": true, "message": "Mã OTP hợp lệ..." }
   ```

3. **POST /api/auth/reset-password**
   ```json
   Request: { "email": "user@example.com", "otp": "123456", "newPassword": "NewPass123" }
   Response: { "success": true, "message": "Đặt lại mật khẩu thành công..." }
   ```

4. **POST /api/user/orders**
   ```json
   Request: { "cartItemIds": [1,2,3], "shippingAddressId": 5, "paymentMethod": "WALLET" }
   Response: { "success": true, "data": { "orderId": 123 } }  // ⚠️ orderId là Integer
   ```

---

## ✅ KẾT LUẬN:

Navigation routes của mày **ĐÃ ĐÚNG PHẦN LỚN**, chỉ cần kiểm tra:

1. ✅ **SuccessScreen component** - Đảm bảo nhận `orderId: Int` (không phải String)
2. ✅ **Navigate calls** - Đảm bảo dùng đúng format `navController.navigate("route/$param")`
3. ✅ **API Response handling** - Đảm bảo parse `orderId` từ response đúng kiểu Int

Nếu mày đảm bảo 3 điểm trên thì **HOÀN TOÀN KHỚP VỚI BACKEND** rồi! 🎉

