# Hướng Dẫn Sử Dụng Hệ Thống Ví Tiền Trong App

## Tổng Quan

Hệ thống ví tiền cho phép người dùng:
- Nạp tiền vào ví trong app
- Tạo đơn hàng (chưa thanh toán)
- **Chọn đơn hàng và thanh toán sau**
- Xem lịch sử giao dịch
- Nhận tiền từ đơn hàng (dành cho seller)

## Luồng Mới: Tạo Đơn → Chọn Đơn → Thanh Toán

### Bước 1: Tạo Đơn Hàng (Chưa Thanh Toán)
Khi tạo đơn hàng, hệ thống chỉ tạo đơn với trạng thái `pending`, KHÔNG tự động thanh toán ngay.

### Bước 2: Xem Danh Sách Đơn Hàng
User có thể xem danh sách đơn hàng của mình, bao gồm các đơn chưa thanh toán.

### Bước 3: Chọn Đơn Và Thanh Toán
User chọn đơn hàng muốn thanh toán và gọi API thanh toán riêng.

## Phương Thức Thanh Toán

### 1. `app_wallet` - Ví trong app
- Đơn hàng được tạo với trạng thái `pending`
- User chọn đơn và gọi API thanh toán
- Tiền tự động trừ từ ví người mua và cộng vào ví người bán
- Trạng thái thanh toán chuyển sang `paid`
- Nếu hủy đơn đã thanh toán, tiền sẽ được hoàn lại tự động

### 2. `ship_cod` - Thanh toán khi nhận hàng
- Giữ trạng thái `pending` cho đến khi giao hàng
- Không thể thanh toán qua API (chỉ thanh toán trực tiếp khi nhận hàng)
- Cơ chế này sẽ được triển khai sau

## API Endpoints

### 1. Tạo Đơn Hàng (Chưa Thanh Toán)

**Endpoint:** `POST /api/user/orders`

**Request Body:**
```json
{
  "deliveryAddressId": 1,
  "paymentMethod": "app_wallet",
  "cartItemIds": [1, 2, 3],
  "deliveryAddress": {
    "addressLine": "123 Nguyễn Huệ",
    "ward": "Phường Bến Nghé",
    "district": "Quận 1",
    "city": "TP Hồ Chí Minh",
    "latitude": 10.7769,
    "longitude": 106.7009
  },
  "notes": "Giao hàng giờ hành chính"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Order created successfully",
  "data": {
    "id": 123,
    "orderNumber": "abc-xyz-123",
    "paymentMethod": "app_wallet",
    "paymentStatus": "pending",
    "orderStatus": "pending",
    "totalAmount": 50000.0,
    ...
  }
}
```

**Lưu ý:** 
- Đơn hàng được tạo với `paymentStatus = "pending"`
- Chưa trừ tiền từ ví
- User cần gọi API thanh toán để hoàn tất

### 2. Xem Danh Sách Đơn Hàng

**Endpoint:** `GET /api/user/orders`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 123,
      "orderNumber": "abc-xyz-123",
      "paymentMethod": "app_wallet",
      "paymentStatus": "pending",
      "orderStatus": "pending",
      "totalAmount": 50000.0,
      ...
    },
    {
      "id": 124,
      "orderNumber": "def-uvw-456",
      "paymentMethod": "app_wallet",
      "paymentStatus": "paid",
      "orderStatus": "confirmed",
      "totalAmount": 75000.0,
      ...
    }
  ]
}
```

### 3. **[MỚI]** Thanh Toán Đơn Hàng

**Endpoint:** `POST /api/user/orders/{orderId}/pay`

**Ví dụ:** `POST /api/user/orders/123/pay`

**Request Body:** Không cần body

**Response:**
```json
{
  "success": true,
  "message": "Thanh toán thành công",
  "data": {
    "id": 123,
    "orderNumber": "abc-xyz-123",
    "paymentMethod": "app_wallet",
    "paymentStatus": "paid",
    "orderStatus": "pending",
    "totalAmount": 50000.0,
    ...
  }
}
```

**Lưu ý:** 
- Chỉ thanh toán được đơn hàng có `paymentStatus = "pending"`
- Chỉ thanh toán được đơn hàng thuộc về mình
- Nếu số dư ví không đủ, API sẽ trả về lỗi
- Tiền sẽ được trừ ngay lập tức khi thanh toán thành công

**Error Cases:**
```json
{
  "success": false,
  "message": "Đơn hàng đã được thanh toán rồi"
}
```

```json
{
  "success": false,
  "message": "Số dư ví không đủ để thanh toán đơn hàng này. Cần: 50000 VND, Có: 30000 VND"
}
```

```json
{
  "success": false,
  "message": "Đơn hàng COD sẽ thanh toán khi nhận hàng"
}
```

### 4. Nạp Tiền Vào Ví

**Endpoint:** `POST /api/user/wallet/deposit`

**Request Body:**
```json
{
  "amount": 100000,
  "description": "Nạp tiền vào ví"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Nạp tiền thành công",
  "data": {
    "userId": 1,
    "fullName": "Nguyễn Văn A",
    "balance": 100000.0,
    "recentTransactions": [...]
  }
}
```

### 5. Xem Thông Tin Ví

**Endpoint:** `GET /api/user/wallet`

### 6. Xem Lịch Sử Giao Dịch

**Endpoint:** `GET /api/user/wallet/transactions`

### 7. Hủy Đơn Hàng

**Endpoint:** `DELETE /api/user/orders/{orderId}`

**Lưu ý:** 
- Nếu đơn đã thanh toán qua ví, tiền sẽ được hoàn lại tự động

## Luồng Xử Lý Thanh Toán

### Khi Tạo Đơn Hàng

1. Tạo đơn hàng với `orderStatus = pending`, `paymentStatus = pending`
2. Lưu thông tin đơn hàng vào database
3. Xóa các CartItem đã chọn
4. Trả về thông tin đơn hàng cho user
5. **KHÔNG trừ/cộng tiền vào ví**

### Khi Thanh Toán Đơn Hàng (app_wallet)

1. Kiểm tra đơn hàng tồn tại
2. Kiểm tra quyền sở hữu (chỉ owner mới thanh toán được)
3. Kiểm tra trạng thái:
   - Đã thanh toán rồi → Lỗi
   - Đã hủy → Lỗi
   - Đang pending → OK
4. Kiểm tra số dư ví người mua
5. Nếu đủ tiền:
   - Trừ tiền từ ví người mua
   - Cộng tiền vào ví người bán (seller)
   - Cập nhật `paymentStatus = paid`
   - Tạo 2 bản ghi transaction:
     - `payment` cho người mua (trừ tiền)
     - `received_payment` cho người bán (cộng tiền)
6. Nếu không đủ tiền:
   - Trả về lỗi: "Số dư ví không đủ"

### Khi Hủy Đơn Hàng (app_wallet đã thanh toán)

1. Kiểm tra đơn hàng đã thanh toán qua ví chưa
2. Nếu đã thanh toán:
   - Hoàn tiền cho người mua
   - Trừ tiền từ người bán
   - Cập nhật `paymentStatus = refunded`
   - Cập nhật `orderStatus = cancelled`
   - Tạo 2 bản ghi transaction loại `refund`

### Khi Thanh Toán Đơn Hàng (ship_cod)

- Không cho phép thanh toán qua API
- Trả về lỗi: "Đơn hàng COD sẽ thanh toán khi nhận hàng"
- Thanh toán sẽ được xử lý khi giao hàng (tính năng này sẽ triển khai sau)

## Testing Flow - Luồng Mới

### Scenario 1: Thanh toán thành công

```bash
# Bước 1: Nạp tiền vào ví
POST /api/user/wallet/deposit
{
  "amount": 500000
}

# Bước 2: Kiểm tra số dư
GET /api/user/wallet
# → balance: 500000

# Bước 3: Tạo đơn hàng (chưa thanh toán)
POST /api/user/orders
{
  "paymentMethod": "app_wallet",
  "cartItemIds": [1, 2, 3],
  ...
}
# → Response: orderId: 123, paymentStatus: "pending"

# Bước 4: Xem danh sách đơn hàng
GET /api/user/orders
# → Thấy đơn 123 với paymentStatus: "pending"

# Bước 5: Thanh toán đơn hàng
POST /api/user/orders/123/pay
# → Response: paymentStatus: "paid"

# Bước 6: Kiểm tra số dư ví (đã bị trừ)
GET /api/user/wallet
# → balance: 450000 (nếu đơn hàng 50000)

# Bước 7: Kiểm tra lịch sử giao dịch
GET /api/user/wallet/transactions
# → Thấy giao dịch "payment" cho đơn 123
```

### Scenario 2: Số dư không đủ

```bash
# Bước 1: Kiểm tra số dư
GET /api/user/wallet
# → balance: 30000

# Bước 2: Tạo đơn hàng 50000
POST /api/user/orders
# → Response: orderId: 124, totalAmount: 50000, paymentStatus: "pending"

# Bước 3: Cố gắng thanh toán
POST /api/user/orders/124/pay
# → Error: "Số dư ví không đủ. Cần: 50000 VND, Có: 30000 VND"

# Bước 4: Nạp thêm tiền
POST /api/user/wallet/deposit
{
  "amount": 50000
}

# Bước 5: Thanh toán lại
POST /api/user/orders/124/pay
# → Success: paymentStatus: "paid"
```

### Scenario 3: Hủy đơn đã thanh toán

```bash
# Bước 1: Thanh toán đơn hàng
POST /api/user/orders/125/pay
# → Success

# Bước 2: Kiểm tra số dư (đã bị trừ)
GET /api/user/wallet
# → balance: 400000

# Bước 3: Hủy đơn hàng
DELETE /api/user/orders/125
# → Success: "Order deleted successfully"

# Bước 4: Kiểm tra số dư (đã được hoàn tiền)
GET /api/user/wallet
# → balance: 450000

# Bước 5: Kiểm tra lịch sử giao dịch
GET /api/user/wallet/transactions
# → Thấy giao dịch "refund" cho đơn 125
```

## Ưu Điểm Của Luồng Mới

1. **Linh hoạt hơn**: User có thể tạo nhiều đơn hàng trước, sau đó quyết định thanh toán đơn nào trước
2. **Kiểm soát tốt hơn**: User có thể xem lại đơn hàng trước khi thanh toán
3. **Trải nghiệm tốt hơn**: Nếu không đủ tiền, user có thể nạp thêm rồi quay lại thanh toán
4. **Tránh lãng phí**: Không tạo đơn hàng rồi phải hủy vì không đủ tiền

## Lưu Ý Quan Trọng

1. **Transaction Safety**: Tất cả các thao tác liên quan đến tiền đều được đặt trong `@Transactional` để đảm bảo tính toàn vẹn dữ liệu

2. **Không Rollback Đơn Hàng**: Nếu thanh toán thất bại, đơn hàng vẫn tồn tại với trạng thái `pending`

3. **Số Dư Âm**: Khi hoàn tiền, nếu seller không đủ tiền, số dư có thể âm (cần xử lý thêm nếu muốn)

4. **Logging**: Tất cả các giao dịch đều được log chi tiết để dễ debug

5. **Security**: API yêu cầu authentication (JWT token) cho tất cả endpoints

## Tính Năng Chưa Triển Khai

- Rút tiền từ ví về tài khoản ngân hàng
- Xử lý thanh toán COD khi giao hàng
- Admin quản lý ví và giao dịch
- Giới hạn số dư tối đa trong ví
- Xác thực OTP khi giao dịch lớn
- Phí giao dịch (nếu cần)
- Tự động hủy đơn chưa thanh toán sau X giờ
