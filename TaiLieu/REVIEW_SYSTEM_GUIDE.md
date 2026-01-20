# 📝 HỆ THỐNG ĐÁNH GIÁ (REVIEW SYSTEM) - GIỐNG SHOPEE

## 🎯 Tính năng

- ✅ Đánh giá shop (sau khi mua hàng)
- ✅ Đánh giá sản phẩm (phải từ đơn hàng đã completed)
- ✅ Rating 1-5 sao
- ✅ Comment + Upload nhiều ảnh
- ✅ Tính điểm trung bình tự động
- ✅ Thống kê rating theo từng mức (1-5 sao)
- ✅ Đánh giá ẩn danh (tùy chọn)
- ✅ **AI CŨng REPLY ĐƯỢC - GIỐNG FACEBOOK COMMENT** 🔥
- ✅ **TỰ ĐỘNG HIỂN THỊ - REVIEW LÀ QUYỀN CON NGƯỜI!** 🔥
- ✅ **ENDPOINT TÁCH BIỆT CHO SHOP VÀ PRODUCT - GIỐNG SHOPEE** 🔥

---

## 🔌 API ENDPOINTS - CẤU TRÚC MỚI

### 👤 USER APIs (Cần đăng nhập)

#### 1️⃣ Đánh giá SHOP
```http
POST /api/user/reviews/shops/{shopId}
Content-Type: multipart/form-data
Authorization: Bearer {token}

Form Data:
- data (JSON):
  {
    "rating": 5,                      // 1-5 sao
    "comment": "Shop giao hàng nhanh, đóng gói cẩn thận!",
    "isAnonymous": false              // true = ẩn danh
  }
- images: [file1.jpg, file2.jpg]    // Tối đa 5 ảnh, mỗi ảnh max 5MB

Ví dụ: POST /api/user/reviews/shops/1
```

**Response:**
```json
{
  "success": true,
  "message": "Đánh giá shop thành công",
  "data": {
    "id": 1,
    "userId": 10,
    "userFullName": "Nguyễn Văn A",
    "userAvatarUrl": "...",
    "reviewableType": "shop",
    "reviewableId": 1,
    "reviewableName": "Quán Phở Ngon",
    "orderId": null,
    "rating": 5,
    "comment": "Shop giao hàng nhanh!",
    "images": ["path/to/image1.jpg"],
    "isAnonymous": false,
    "adminReply": null,
    "isApproved": true,
    "createdAt": "2026-01-16T22:30:00",
    "updatedAt": "2026-01-16T22:30:00"
  }
}
```

#### 2️⃣ Đánh giá PRODUCT (từ đơn hàng cụ thể)
```http
POST /api/user/reviews/products/{productId}/orders/{orderId}
Content-Type: multipart/form-data
Authorization: Bearer {token}

Form Data:
- data (JSON):
  {
    "rating": 5,                      // 1-5 sao
    "comment": "Phở rất ngon, nước dùng đậm đà!",
    "isAnonymous": false
  }
- images: [file1.jpg, file2.jpg]

Ví dụ: POST /api/user/reviews/products/25/orders/100
→ Đánh giá product ID 25 từ order ID 100
```

**Validate:**
- ✅ Order 100 phải thuộc về user
- ✅ Order 100 phải có status = "delivered"
- ✅ Product 25 phải có trong order 100
- ✅ User chưa đánh giá product 25

**Response:**
```json
{
  "success": true,
  "message": "Đánh giá sản phẩm thành công",
  "data": {
    "id": 2,
    "reviewableType": "product",
    "reviewableId": 25,
    "reviewableName": "Phở bò",
    "orderId": 100,
    "rating": 5,
    "comment": "Phở rất ngon!",
    "images": ["..."]
  }
}
```

#### 3️⃣ Cập nhật đánh giá
```http
PUT /api/user/reviews/{reviewId}
Content-Type: multipart/form-data
Authorization: Bearer {token}

Form Data:
- data (JSON):
  {
    "rating": 4,
    "comment": "Update: Vẫn ngon nhưng hơi mặn",
    "isAnonymous": false
  }
- images: [file1.jpg]  // Ảnh mới sẽ thay thế ảnh cũ
```

#### 4️⃣ Xóa đánh giá
```http
DELETE /api/user/reviews/{reviewId}
Authorization: Bearer {token}
```

#### 5️⃣ Xem đánh giá của mình
```http
GET /api/user/reviews/my-reviews?page=0&size=10
Authorization: Bearer {token}
```

#### 6️⃣ Trả lời đánh giá (ai cũng reply được - giống Facebook)
```http
POST /api/user/reviews/{reviewId}/reply
Authorization: Bearer {token}

Body:
{
  "adminReply": "Cảm ơn bạn đã ủng hộ shop! 🙏"
}
```

---

### 🌐 PUBLIC APIs (Không cần đăng nhập)

#### 1️⃣ Xem đánh giá của SHOP (giống Shopee - bấm vào shop là thấy review)
```http
GET /api/public/reviews/shops/{shopId}?page=0&size=10&sortBy=createdAt&sortDir=DESC

Ví dụ: GET /api/public/reviews/shops/1
→ Lấy tất cả review của shop ID 1
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userFullName": "Nguyễn Văn A",
      "rating": 5,
      "comment": "Shop giao hàng nhanh!",
      "images": ["..."],
      "adminReply": "Cảm ơn bạn!",
      "createdAt": "2026-01-16T10:00:00"
    }
  ],
  "currentPage": 0,
  "totalItems": 50,
  "totalPages": 5
}
```

#### 2️⃣ Xem đánh giá của PRODUCT (giống Shopee - kéo xuống dưới product là thấy review)
```http
GET /api/public/reviews/products/{productId}?page=0&size=10

Ví dụ: GET /api/public/reviews/products/25
→ Lấy tất cả review của product ID 25
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 2,
      "userFullName": "Trần Thị B",
      "rating": 5,
      "comment": "Phở ngon lắm!",
      "images": ["..."],
      "adminReply": null,
      "createdAt": "2026-01-16T11:00:00"
    }
  ],
  "currentPage": 0,
  "totalItems": 120,
  "totalPages": 12
}
```

#### 3️⃣ Thống kê rating SHOP
```http
GET /api/public/reviews/shops/{shopId}/statistics

Ví dụ: GET /api/public/reviews/shops/1/statistics
```

**Response:**
```json
{
  "success": true,
  "data": {
    "averageRating": 4.5,       // Điểm trung bình
    "totalReviews": 100,         // Tổng số đánh giá
    "fiveStarCount": 60,
    "fourStarCount": 30,
    "threeStarCount": 8,
    "twoStarCount": 2,
    "oneStarCount": 0
  }
}
```

#### 4️⃣ Thống kê rating PRODUCT
```http
GET /api/public/reviews/products/{productId}/statistics

Ví dụ: GET /api/public/reviews/products/25/statistics
```

#### 5️⃣ Xem chi tiết 1 đánh giá
```http
GET /api/public/reviews/{reviewId}
```

---

## 🎨 USE CASES - GIỐNG SHOPEE

### Use Case 1: User đánh giá sản phẩm sau khi nhận hàng
```
📱 Flow giống Shopee:

1. User đặt món (tạo order)
2. Shop giao hàng
3. Order status = "delivered"
4. App hiển thị nút "Đánh giá" trong lịch sử đơn hàng
5. User bấm "Đánh giá":
   - Chọn số sao (1-5)
   - Viết comment
   - Chọn ảnh (tùy chọn)
   - Bấm "Gửi đánh giá"
6. App gọi API:
   POST /api/user/reviews/products/25/orders/100
   
   Form data:
   - data: {"rating": 5, "comment": "Ngon!", "isAnonymous": false}
   - images: [ảnh món ăn]
   
7. ✅ Đánh giá hiển thị ngay dưới trang sản phẩm
```

### Use Case 2: User đánh giá shop (tổng thể)
```
📱 Flow:

1. User vào trang shop
2. Bấm nút "Đánh giá shop"
3. Viết đánh giá về:
   - Thái độ phục vụ
   - Tốc độ giao hàng
   - Chất lượng đóng gói
4. App gọi API:
   POST /api/user/reviews/shops/1
   
   Form data:
   - data: {"rating": 5, "comment": "Shop tuyệt vời!", "isAnonymous": false}
   
5. ✅ Đánh giá hiển thị ở tab "Đánh giá shop"
```

### Use Case 3: Khách xem đánh giá trước khi mua (giống Shopee)
```
📱 Flow:

1. Khách vào trang shop
   → Gọi: GET /api/public/reviews/shops/1/statistics
   → Hiển thị: ⭐ 4.5/5.0 (100 đánh giá)
   
2. Khách bấm "Xem đánh giá shop"
   → Gọi: GET /api/public/reviews/shops/1?page=0
   → Hiển thị danh sách review của shop

3. Khách vào trang product
   → Gọi: GET /api/public/reviews/products/25/statistics
   → Hiển thị: ⭐ 4.8/5.0 (120 đánh giá)
   
4. Khách kéo xuống dưới
   → Gọi: GET /api/public/reviews/products/25?page=0
   → Hiển thị danh sách review của món ăn đó
```

### Use Case 4: Ai cũng trả lời được review (giống Facebook comment)
```
📱 Flow:

1. User A đánh giá shop
2. Chủ shop/User B/Admin thấy review
3. Bất kỳ ai cũng có thể reply:
   POST /api/user/reviews/1/reply
   {
     "adminReply": "Cảm ơn bạn! ❤️"
   }
4. Reply hiển thị ngay dưới review
```

---

## 🏗️ CẤU TRÚC ENDPOINT MỚI

### So sánh cũ vs mới:

#### ❌ Cũ (phức tạp):
```
POST /api/user/reviews
Body: {
  "reviewableType": "shop",    ← Phải điền tay
  "reviewableId": 1,
  "rating": 5
}
```

#### ✅ Mới (rõ ràng - giống Shopee):
```
POST /api/user/reviews/shops/{shopId}
→ Rõ ràng là đang review shop

POST /api/user/reviews/products/{productId}/orders/{orderId}
→ Rõ ràng là review product từ order nào
```

### Lợi ích:
- ✅ Frontend không cần truyền `reviewableType` nữa
- ✅ Endpoint tự động biết đang review shop hay product
- ✅ Dễ hiểu, dễ maintain
- ✅ Giống Shopee - UX tốt hơn

---

## ⚙️ BUSINESS LOGIC

### Quy tắc đánh giá Product:
- ✅ Phải có `orderId` (lấy từ endpoint)
- ✅ Order phải thuộc về user
- ✅ Order status phải là `delivered`
- ✅ Product phải có trong order đó
- ✅ Mỗi user chỉ đánh giá 1 lần cho 1 product

### Quy tắc đánh giá Shop:
- ✅ `orderId` không bắt buộc
- ✅ Mỗi user chỉ đánh giá 1 lần cho 1 shop

### Quyền trả lời review:
- ✅ **AI CŨNG REPLY ĐƯỢC** - Giống Facebook comment
- ✅ User thường, Seller, Admin đều reply được
- ✅ Tăng tính tương tác

---

## 📊 POSTMAN EXAMPLES

### 1. Đánh giá shop
```
POST http://localhost:8080/api/user/reviews/shops/1
Headers:
  Authorization: Bearer {token}
  
Body (form-data):
  data: {
    "rating": 5,
    "comment": "Shop tuyệt vời!",
    "isAnonymous": false
  }
  images: [chọn file ảnh]
```

### 2. Đánh giá product
```
POST http://localhost:8080/api/user/reviews/products/25/orders/100
Headers:
  Authorization: Bearer {token}
  
Body (form-data):
  data: {
    "rating": 5,
    "comment": "Phở ngon!",
    "isAnonymous": false
  }
  images: [chọn file ảnh món ăn]
```

### 3. Xem review shop (Public)
```
GET http://localhost:8080/api/public/reviews/shops/1?page=0&size=10
```

### 4. Xem review product (Public)
```
GET http://localhost:8080/api/public/reviews/products/25?page=0&size=10
```

### 5. Xem thống kê shop
```
GET http://localhost:8080/api/public/reviews/shops/1/statistics
```

### 6. Xem thống kê product
```
GET http://localhost:8080/api/public/reviews/products/25/statistics
```

### 7. Reply review
```
POST http://localhost:8080/api/user/reviews/1/reply
Headers:
  Authorization: Bearer {token}
  
Body:
{
  "adminReply": "Cảm ơn bạn! ❤️"
}
```

---

## 🎯 VALIDATE CHẶT CHẼ

### Review Product:
```
1. Check order tồn tại
2. Check order thuộc về user
3. Check order.status = "delivered"
4. Check product có trong order
5. Check chưa review product này
6. ✅ Tạo review
```

### Review Shop:
```
1. Check shop tồn tại
2. Check chưa review shop này
3. ✅ Tạo review
```

---

## 📱 FRONTEND INTEGRATION

### Trang Shop:
```javascript
// Lấy thống kê
const stats = await fetch('/api/public/reviews/shops/1/statistics');
// Hiển thị: ⭐ 4.5 (100 đánh giá)

// Lấy danh sách review
const reviews = await fetch('/api/public/reviews/shops/1?page=0');
// Hiển thị danh sách
```

### Trang Product:
```javascript
// Lấy thống kê
const stats = await fetch('/api/public/reviews/products/25/statistics');
// Hiển thị: ⭐ 4.8 (120 đánh giá)

// Kéo xuống dưới → load review
const reviews = await fetch('/api/public/reviews/products/25?page=0');
// Hiển thị danh sách review món ăn
```

---

## 🚀 SUMMARY

✅ **Đã sửa:**
- Tách riêng endpoint cho Shop và Product
- shopId/productId lên URL (giống Shopee)
- Dễ hiểu, dễ dùng hơn
- AI cũng reply được review

✅ **Cấu trúc mới:**
```
POST   /api/user/reviews/shops/{shopId}
POST   /api/user/reviews/products/{productId}/orders/{orderId}
GET    /api/public/reviews/shops/{shopId}
GET    /api/public/reviews/products/{productId}
GET    /api/public/reviews/shops/{shopId}/statistics
GET    /api/public/reviews/products/{productId}/statistics
POST   /api/user/reviews/{reviewId}/reply
```

🎉 **HOÀN THÀNH!**
