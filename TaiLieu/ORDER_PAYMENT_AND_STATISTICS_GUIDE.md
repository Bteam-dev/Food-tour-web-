# HƯỚNG DẪN SỬ DỤNG HỆ THỐNG THANH TOÁN VÀ THỐNG KÊ DOANH THU

## 📋 MỤC LỤC
1. [Hệ thống thanh toán đơn hàng](#1-hệ-thống-thanh-toán-đơn-hàng)
2. [Quản lý đơn hàng cho Seller](#2-quản-lý-đơn-hàng-cho-seller)
3. [Thống kê doanh thu](#3-thống-kê-doanh-thu)
4. [Dashboard tổng quan](#4-dashboard-tổng-quan)

---

## 1. HỆ THỐNG THANH TOÁN ĐƠN HÀNG

### 1.1. Phương thức thanh toán

Hệ thống hỗ trợ **2 phương thức thanh toán** cho đơn hàng:

⚠️ **LƯU Ý**: MOMO chỉ dùng để **nạp tiền vào ví**, KHÔNG dùng để thanh toán đơn hàng!

#### A. **APP_WALLET** (Ví trong app)
- User thanh toán bằng số dư trong ví
- Tiền sẽ được **trừ ngay** khi user gọi API thanh toán
- Luồng xử lý:
  1. User tạo đơn hàng → Trạng thái: `pending`
  2. User gọi API thanh toán → Hệ thống trừ tiền từ ví
  3. **Buyer** trả: `totalAmount`
  4. **Seller** nhận: `totalAmount - 12%` (sau khi trừ hoa hồng)
  5. **Admin** nhận: `12%` hoa hồng platform
  6. Trạng thái chuyển sang: `paid`

#### B. **SHIP_COD** (Tiền mặt)
- Thanh toán khi nhận hàng (Cash On Delivery)
- Không trừ tiền từ ví
- Luồng xử lý:
  1. User tạo đơn hàng → Trạng thái: `pending`
  2. Shipper giao hàng và nhận tiền mặt từ user
  3. Shipper chuyển tiền cho shop
  4. **Shop xác nhận** đã nhận tiền → Gọi API confirm COD
  5. Hệ thống tự động tính hoa hồng 12% cho admin
  6. Trạng thái chuyển sang: `paid` và `delivered`

### 1.2. Giá trị paymentMethod được chấp nhận

Khi tạo đơn hàng, bạn có thể gửi các giá trị sau cho `paymentMethod`:

**Cho WALLET:**
- `"WALLET"` ✅
- `"APP_WALLET"` ✅

**Cho COD:**
- `"COD"` ✅
- `"CASH"` ✅
- `"SHIP_COD"` ✅

**Không được chấp nhận:**
- `"MOMO"` ❌ (chỉ dùng để nạp tiền vào ví)
- Các giá trị khác ❌

**Response lỗi khi dùng sai:**
```json
{
  "success": false,
  "message": "Invalid payment method: MOMO. Only WALLET (app wallet) and COD (cash on delivery) are allowed."
}
```

---

## 2. QUẢN LÝ ĐƠN HÀNG CHO SELLER

### 2.1. API Endpoints

#### **GET** `/api/seller/orders`
Lấy danh sách đơn hàng của shop

**Query Params:**
- `page` (default: 0)
- `size` (default: 10)
- `sortBy` (default: "createdAt")
- `sortDir` (default: "DESC")

**Response:**
```json
{
  "success": true,
  "data": {
    "content": [
      {
        "id": 1,
        "orderNumber": "uuid-string",
        "userName": "Nguyễn Văn A",
        "shopName": "Quán Ăn ABC",
        "orderStatus": "pending",
        "paymentStatus": "pending",
        "paymentMethod": "app_wallet",
        "totalAmount": 150000,
        "platformCommissionAmount": 18000,
        "sellerReceivedAmount": 132000,
        "createdAt": "2024-01-15T10:30:00"
      }
    ],
    "totalPages": 5,
    "totalElements": 50,
    "currentPage": 0
  }
}
```

---

#### **GET** `/api/seller/orders/filter`
Lọc đơn hàng theo trạng thái và thời gian

**Query Params:**
- `status` (optional): "pending" | "confirmed" | "preparing" | "ready" | "delivering" | "delivered" | "cancelled"
- `startDate` (optional): ISO 8601 format (e.g., "2024-01-01T00:00:00")
- `endDate` (optional): ISO 8601 format (e.g., "2024-01-31T23:59:59")
- `page`, `size`, `sortBy`, `sortDir` (giống API trên)

**Ví dụ:**
```
GET /api/seller/orders/filter?status=delivered&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59&page=0&size=20
```

**Response:** Giống API `/api/seller/orders`

---

#### **POST** `/api/seller/orders/{orderId}/confirm-cod`
Seller xác nhận đã nhận tiền COD từ shipper

**Path Params:**
- `orderId`: ID của đơn hàng

**Response:**
```json
{
  "success": true,
  "message": "Xác nhận đã nhận tiền COD thành công. Đơn hàng hoàn tất.",
  "data": {
    "id": 1,
    "orderNumber": "uuid-string",
    "orderStatus": "delivered",
    "paymentStatus": "paid",
    "paymentMethod": "ship_cod",
    "totalAmount": 150000,
    "platformCommissionRate": 12.00,
    "platformCommissionAmount": 18000,
    "sellerReceivedAmount": 132000,
    "actualDeliveryTime": "2024-01-15T15:30:00"
  }
}
```

**Lưu ý:**
- Chỉ đơn hàng COD mới được phép gọi API này
- Đơn hàng phải chưa được thanh toán
- Sau khi xác nhận, đơn hàng tự động chuyển sang `delivered`

---

## 3. THỐNG KÊ DOANH THU

### 3.1. Thống kê doanh thu theo ngày/tháng/năm

#### **GET** `/api/seller/orders/statistics/revenue`

**Query Params:**
- `periodType` (required): "day" | "month" | "year"
- `startDate` (required): ISO 8601 format
- `endDate` (required): ISO 8601 format

**Ví dụ:**

**A. Thống kê theo ngày:**
```
GET /api/seller/orders/statistics/revenue?periodType=day&startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "period": "2024-01-01",
      "totalOrders": 15,
      "totalRevenue": 3500000,
      "platformCommission": 420000,
      "sellerRevenue": 3080000
    },
    {
      "period": "2024-01-02",
      "totalOrders": 20,
      "totalRevenue": 4200000,
      "platformCommission": 504000,
      "sellerRevenue": 3696000
    }
  ]
}
```

**B. Thống kê theo tháng:**
```
GET /api/seller/orders/statistics/revenue?periodType=month&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "period": "2024-01",
      "totalOrders": 450,
      "totalRevenue": 85000000,
      "platformCommission": 10200000,
      "sellerRevenue": 74800000
    },
    {
      "period": "2024-02",
      "totalOrders": 520,
      "totalRevenue": 95000000,
      "platformCommission": 11400000,
      "sellerRevenue": 83600000
    }
  ]
}
```

**C. Thống kê theo năm:**
```
GET /api/seller/orders/statistics/revenue?periodType=year&startDate=2020-01-01T00:00:00&endDate=2024-12-31T23:59:59
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "period": "2023",
      "totalOrders": 5400,
      "totalRevenue": 980000000,
      "platformCommission": 117600000,
      "sellerRevenue": 862400000
    },
    {
      "period": "2024",
      "totalOrders": 6200,
      "totalRevenue": 1150000000,
      "platformCommission": 138000000,
      "sellerRevenue": 1012000000
    }
  ]
}
```

---

### 3.2. Thống kê sản phẩm bán chạy

#### **GET** `/api/seller/orders/statistics/top-products`

**Query Params:**
- `startDate` (required): ISO 8601 format
- `endDate` (required): ISO 8601 format
- `limit` (default: 10): Số lượng sản phẩm muốn lấy

**Ví dụ:**
```
GET /api/seller/orders/statistics/top-products?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59&limit=5
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "productId": 10,
      "productName": "Phở Bò Đặc Biệt",
      "productImageUrl": "https://example.com/pho-bo.jpg",
      "totalQuantitySold": 250,
      "totalOrders": 180,
      "totalRevenue": 25000000,
      "sellerRevenue": 22000000
    },
    {
      "productId": 15,
      "productName": "Bún Chả Hà Nội",
      "productImageUrl": "https://example.com/bun-cha.jpg",
      "totalQuantitySold": 180,
      "totalOrders": 150,
      "totalRevenue": 18000000,
      "sellerRevenue": 15840000
    }
  ]
}
```

**Giải thích:**
- `totalQuantitySold`: Tổng số lượng sản phẩm đã bán
- `totalOrders`: Số đơn hàng có chứa sản phẩm này
- `totalRevenue`: Tổng doanh thu từ sản phẩm (chưa trừ hoa hồng)
- `sellerRevenue`: Doanh thu seller nhận được (đã trừ 12% hoa hồng)

---

## 4. DASHBOARD TỔNG QUAN

#### **GET** `/api/seller/orders/statistics/dashboard`

**Query Params:**
- `startDate` (required): ISO 8601 format
- `endDate` (required): ISO 8601 format

**Ví dụ:**
```
GET /api/seller/orders/statistics/dashboard?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59
```

**Response:**
```json
{
  "success": true,
  "data": {
    "totalOrders": 1500,
    "pendingOrders": 45,
    "completedOrders": 1320,
    "cancelledOrders": 135,
    "totalRevenue": 285000000,
    "platformCommission": 34200000,
    "sellerRevenue": 250800000,
    "topSellingProducts": [
      {
        "productId": 10,
        "productName": "Phở Bò Đặc Biệt",
        "productImageUrl": "https://example.com/pho-bo.jpg",
        "totalQuantitySold": 250,
        "totalOrders": 180,
        "totalRevenue": 25000000,
        "sellerRevenue": 22000000
      }
    ]
  }
}
```

**Giải thích:**
- `totalOrders`: Tổng số đơn hàng (tất cả thời gian)
- `pendingOrders`: Đơn hàng đang chờ xử lý
- `completedOrders`: Đơn hàng đã hoàn thành
- `cancelledOrders`: Đơn hàng đã hủy
- `totalRevenue`: Tổng doanh thu trong khoảng thời gian (đã thanh toán)
- `platformCommission`: Tổng hoa hồng platform 12%
- `sellerRevenue`: Doanh thu thực nhận của seller
- `topSellingProducts`: Top 10 sản phẩm bán chạy

---

## 5. LUỒNG SỬ DỤNG THỰC TẾ

### 5.1. Luồng thanh toán bằng ví (APP_WALLET)

```
1. User thêm sản phẩm vào giỏ hàng
2. User tạo đơn hàng với paymentMethod = "WALLET"
   POST /api/user/orders
   {
     "paymentMethod": "WALLET",
     "cartItemIds": [1, 2, 3],
     "deliveryAddress": {...}
   }

3. User nhận được đơn hàng với paymentStatus = "pending"

4. User thanh toán đơn hàng
   POST /api/user/orders/{orderId}/pay
   → Hệ thống tự động:
     - Trừ tiền từ ví user
     - Cộng tiền vào ví seller (đã trừ 12% hoa hồng)
     - Cộng 12% hoa hồng vào ví admin
     - Chuyển paymentStatus = "paid"

5. Shop xử lý đơn hàng và giao hàng
```

### 5.2. Luồng thanh toán COD (SHIP_COD)

```
1. User thêm sản phẩm vào giỏ hàng
2. User tạo đơn hàng với paymentMethod = "COD"
   POST /api/user/orders
   {
     "paymentMethod": "COD",
     "cartItemIds": [1, 2, 3],
     "deliveryAddress": {...}
   }

3. User nhận được đơn hàng với paymentStatus = "pending"

4. Shop chuẩn bị hàng và giao cho shipper

5. Shipper giao hàng và nhận tiền mặt từ user

6. Shipper chuyển tiền cho shop (ngoài đời thực)

7. Shop xác nhận đã nhận tiền
   POST /api/seller/orders/{orderId}/confirm-cod
   → Hệ thống tự động:
     - Tính hoa hồng 12% cho admin
     - Chuyển paymentStatus = "paid"
     - Chuyển orderStatus = "delivered"
```

### 5.3. Luồng xem thống kê doanh thu

```
1. Seller đăng nhập và vào trang dashboard

2. Xem tổng quan (Dashboard)
   GET /api/seller/orders/statistics/dashboard?startDate=...&endDate=...
   → Hiển thị: Tổng đơn hàng, doanh thu, top sản phẩm

3. Xem thống kê theo tháng
   GET /api/seller/orders/statistics/revenue?periodType=month&startDate=...&endDate=...
   → Hiển thị biểu đồ doanh thu theo từng tháng

4. Xem sản phẩm bán chạy
   GET /api/seller/orders/statistics/top-products?startDate=...&endDate=...&limit=20
   → Hiển thị danh sách top 20 sản phẩm

5. Lọc đơn hàng theo trạng thái
   GET /api/seller/orders/filter?status=delivered&startDate=...&endDate=...
   → Hiển thị các đơn đã giao thành công
```

---

## 6. LƯU Ý QUAN TRỌNG

### 6.1. Hoa hồng Platform
- **Tỷ lệ**: 12% trên mỗi đơn hàng
- **Áp dụng**: Cho cả thanh toán WALLET và COD
- **Tính toán**:
  - `platformCommission = totalAmount × 12%`
  - `sellerRevenue = totalAmount - platformCommission`

### 6.2. Trạng thái đơn hàng (OrderStatus)
- `pending`: Đơn hàng mới tạo, chờ xử lý
- `confirmed`: Shop đã xác nhận
- `preparing`: Đang chuẩn bị
- `ready`: Sẵn sàng giao hàng
- `delivering`: Đang giao hàng
- `delivered`: Đã giao thành công
- `cancelled`: Đã hủy
- `refunded`: Đã hoàn tiền

### 6.3. Trạng thái thanh toán (PaymentStatus)
- `pending`: Chưa thanh toán
- `paid`: Đã thanh toán
- `failed`: Thanh toán thất bại
- `refunded`: Đã hoàn tiền

### 6.4. Format thời gian
- Sử dụng ISO 8601: `YYYY-MM-DDTHH:mm:ss`
- Ví dụ: `2024-01-15T10:30:00`
- Timezone: UTC hoặc local timezone của server

---

## 7. XỬ LÝ LỖI

### 7.1. Lỗi thường gặp

**A. Không đủ tiền trong ví**
```json
{
  "success": false,
  "message": "Số dư ví không đủ để thanh toán đơn hàng này. Cần: 150000 VND, Có: 100000 VND"
}
```

**B. Đơn hàng không phải COD**
```json
{
  "success": false,
  "message": "Chỉ đơn hàng COD mới cần xác nhận thanh toán"
}
```

**C. Không có shop**
```json
{
  "success": false,
  "message": "Bạn chưa có shop nào"
}
```

**D. Invalid period type**
```json
{
  "success": false,
  "message": "Invalid period type. Allowed: day, month, year"
}
```

---

## 8. TESTING

### 8.1. Test thanh toán WALLET
```bash
# 1. Tạo đơn hàng
curl -X POST http://localhost:8080/api/user/orders \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "paymentMethod": "WALLET",
    "cartItemIds": [1, 2],
    "deliveryAddress": {...}
  }'

# 2. Thanh toán đơn hàng
curl -X POST http://localhost:8080/api/user/orders/1/pay \
  -H "Authorization: Bearer YOUR_TOKEN"
```

### 8.2. Test xác nhận COD
```bash
# Seller xác nhận đã nhận tiền COD
curl -X POST http://localhost:8080/api/seller/orders/1/confirm-cod \
  -H "Authorization: Bearer SELLER_TOKEN"
```

### 8.3. Test thống kê
```bash
# Dashboard tổng quan
curl -X GET "http://localhost:8080/api/seller/orders/statistics/dashboard?startDate=2024-01-01T00:00:00&endDate=2024-01-31T23:59:59" \
  -H "Authorization: Bearer SELLER_TOKEN"

# Thống kê theo tháng
curl -X GET "http://localhost:8080/api/seller/orders/statistics/revenue?periodType=month&startDate=2024-01-01T00:00:00&endDate=2024-12-31T23:59:59" \
  -H "Authorization: Bearer SELLER_TOKEN"
```

---

## 9. KẾT LUẬN

Hệ thống đã được triển khai đầy đủ với:
✅ 2 phương thức thanh toán: WALLET và COD
✅ Tự động tính hoa hồng 12% cho admin
✅ API lọc đơn hàng theo trạng thái và thời gian
✅ Thống kê doanh thu theo ngày/tháng/năm
✅ Thống kê sản phẩm bán chạy
✅ Dashboard tổng quan cho seller
✅ Xác nhận đơn COD cho seller

**Liên hệ hỗ trợ:** Nếu có vấn đề, hãy kiểm tra logs trong console hoặc database.
