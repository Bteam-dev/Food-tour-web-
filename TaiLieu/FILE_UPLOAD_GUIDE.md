# HƯỚNG DẪN UPLOAD FILE - FORM-DATA

## 📁 CẤU TRÚC THỦ MỤC LƯU TRỮ FILE

File được lưu tại: `D:\Project\BackEnd\FoodTourApp_BE\StorageFile\`

### Các thư mục con (với subfolder ID để phân biệt):
```
StorageFile/
├── ProductImage/       # Ảnh sản phẩm (món ăn)
│   ├── shop_1/        # Ảnh của các product thuộc shop 1
│   ├── shop_2/        # Ảnh của các product thuộc shop 2
│   ├── product_5/     # Ảnh update của product 5
│   └── ...
├── ShopLogo/          # Logo cửa hàng
│   ├── seller_1/      # Logo của seller 1 (khi tạo shop)
│   ├── shop_1/        # Logo của shop 1 (khi update shop)
│   └── ...
├── ShopBanner/        # Banner/Ảnh bìa cửa hàng  
│   ├── seller_1/      # Banner của seller 1 (khi tạo shop)
│   ├── shop_1/        # Banner của shop 1 (khi update shop)
│   └── ...
├── ReviewImage/       # Ảnh đánh giá (review)
│   ├── user_2/        # Review images của user 2
│   ├── user_5/        # Review images của user 5
│   └── ...
├── UserAvatar/        # Avatar người dùng
└── FileMessage/       # File gửi trong chat
```

### 🎯 LỢI ÍCH CỦA SUBFOLDER ID:
✅ **Dễ phân biệt**: Biết ngay file nào thuộc shop/product/user nào  
✅ **Dễ quản lý**: Có thể xóa hàng loạt file của 1 shop/user  
✅ **Dễ debug**: Tìm file nhanh hơn khi có vấn đề  
✅ **Tổ chức rõ ràng**: Không bị lộn xộn khi có hàng ngàn file  

---

## 🏪 1. TẠO SHOP (Create Shop)

### Endpoint
```
POST /api/seller/shops
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của CreateShopRequestDTO |
| `logo` | File | ❌ No | File ảnh logo (jpg/png, max 5MB) |
| `banner` | File | ❌ No | File ảnh banner (jpg/png, max 5MB) |

### 📂 File sẽ được lưu vào:
- Logo: `ShopLogo/seller_X/timestamp-uuid.jpg`  
- Banner: `ShopBanner/seller_X/timestamp-uuid.jpg`  
  _(X = seller ID, vì lúc tạo chưa có shop ID)_

### Ví dụ JSON cho field `data`:
```json
{
  "shopName": "Quán Ăn Ngon",
  "description": "Món ăn địa phương đặc sản",
  "phone": "0905123456",
  "email": "quan@example.com",
  "businessLicense": "BL-123456",
  "taxCode": "TAX-987654",
  "address": {
    "addressLine": "123 Nguyễn Văn Linh",
    "ward": "Hòa Xuân",
    "district": "Cẩm Lệ",
    "city": "Đà Nẵng",
    "country": "Vietnam",
    "postalCode": "550000",
    "latitude": 16.0544,
    "longitude": 108.2022
  },
  "openingHours": "{\"monday\":{\"open\":\"08:00\",\"close\":\"22:00\"}}"
}
```

### ❗ QUAN TRỌNG:
- **KHÔNG** cần gửi `logoUrl` hoặc `bannerUrl` trong JSON nữa
- Chỉ cần upload file vào field `logo` và `banner`
- Backend sẽ tự động lưu file và gán URL vào database

### Cách làm trong Postman:
1. Chọn method: `POST`
2. URL: `http://localhost:8080/api/seller/shops`
3. Headers: Add `Authorization: Bearer <token>`
4. Body → Chọn `form-data`
5. Add key `data` → Type: Text → Value: Paste JSON ở trên
6. Add key `logo` → Type: File → Chọn file ảnh logo
7. Add key `banner` → Type: File → Chọn file ảnh banner
8. Click Send

---

## 🏪 2. CẬP NHẬT SHOP (Update Shop)

### Endpoint
```
PUT /api/seller/shops/{shopId}
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của UpdateShopRequestDTO |
| `logo` | File | ❌ No | File ảnh logo mới (nếu muốn đổi) |
| `banner` | File | ❌ No | File ảnh banner mới (nếu muốn đổi) |

### 📂 File sẽ được lưu vào:
- Logo: `ShopLogo/shop_1/timestamp-uuid.jpg`  
- Banner: `ShopBanner/shop_1/timestamp-uuid.jpg`  
  _(Lúc update đã có shop ID nên dùng shop_X)_

### Ví dụ JSON cho field `data`:
```json
{
  "shopName": "Quán Ăn Ngon Cập Nhật",
  "description": "Món ăn địa phương đặc sản - đã cập nhật",
  "phone": "0905123456",
  "email": "quan@example.com",
  "openingHours": "{\"monday\":{\"open\":\"08:00\",\"close\":\"23:00\"}}"
}
```

---

## 🍜 3. TẠO SẢN PHẨM (Create Product)

### Endpoint
```
POST /api/seller/products
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của CreateProductRequestDTO |
| `images` | File[] | ❌ No | Nhiều file ảnh sản phẩm (có thể chọn nhiều) |

### 📂 File sẽ được lưu vào:
- Images: `ProductImage/shop_5/timestamp-uuid.jpg`  
  _(shop_5 = shopId trong request data)_

### Ví dụ JSON cho field `data`:
```json
{
  "shopId": 5,
  "categoryId": 1,
  "name": "Phở Bò Đặc Biệt",
  "description": "Phở bò nấu theo công thức gia truyền",
  "price": 50000,
  "stockQuantity": 100,
  "isActive": true
}
```

### Cách làm trong Postman:
1. Chọn method: `POST`
2. URL: `http://localhost:8080/api/seller/products`
3. Headers: Add `Authorization: Bearer <token>`
4. Body → Chọn `form-data`
5. Add key `data` → Type: Text → Value: Paste JSON ở trên
6. Add key `images` → Type: File → **Click chọn nhiều file ảnh cùng lúc**
7. Click Send

### ❗ QUAN TRỌNG:
- **KHÔNG** cần gửi `imageUrls` trong JSON nữa
- Chỉ cần upload nhiều file vào field `images`
- Ảnh sẽ được lưu vào thư mục `ProductImage/shop_X/` để phân biệt theo shop
- Backend sẽ tự động lưu tất cả ảnh và gán URLs vào database

---

## 🍜 4. CẬP NHẬT SẢN PHẨM (Update Product)

### Endpoint
```
PUT /api/seller/products/{productId}
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của UpdateProductRequestDTO |
| `images` | File[] | ❌ No | Nhiều file ảnh mới (nếu muốn đổi ảnh) |

### 📂 File sẽ được lưu vào:
- Images: `ProductImage/product_10/timestamp-uuid.jpg`  
  _(product_10 = productId trong path parameter)_

### Ví dụ JSON cho field `data`:
```json
{
  "name": "Phở Bò Đặc Biệt - Đã cập nhật",
  "description": "Phở bò nấu theo công thức gia truyền - món mới",
  "price": 55000,
  "stockQuantity": 150
}
```

---

## ⭐ 5. TẠO REVIEW CHO SHOP

### Endpoint
```
POST /api/user/reviews/shops/{shopId}
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của CreateReviewRequest |
| `images` | File[] | ❌ No | Ảnh đánh giá (có thể nhiều ảnh) |

### 📂 File sẽ được lưu vào:
- Images: `ReviewImage/user_2/timestamp-uuid.jpg`  
  _(user_2 = ID của user đang đăng nhập)_

### Ví dụ JSON cho field `data`:
```json
{
  "rating": 5,
  "comment": "Quán ăn rất ngon, phục vụ tốt!",
  "isAnonymous": false
}
```

### ❗ LƯU Ý:
- `reviewableType` sẽ tự động set thành "shop" (backend override)
- `reviewableId` sẽ tự động lấy từ path parameter `{shopId}`
- Chỉ cần điền `rating` và `comment`

---

## ⭐ 6. TẠO REVIEW CHO SẢN PHẨM

### Endpoint
```
POST /api/user/reviews/products/{productId}/orders/{orderId}
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của CreateReviewRequest |
| `images` | File[] | ❌ No | Ảnh đánh giá sản phẩm |

### 📂 File sẽ được lưu vào:
- Images: `ReviewImage/user_2/timestamp-uuid.jpg`

### Ví dụ JSON cho field `data`:
```json
{
  "rating": 5,
  "comment": "Món ăn rất ngon, đóng gói cẩn thận!",
  "isAnonymous": false
}
```

### ❗ LƯU Ý:
- Phải có `orderId` - chỉ đánh giá được sản phẩm đã mua
- Order phải có status = `delivered` (đã giao hàng)
- Chỉ đánh giá được 1 lần cho mỗi sản phẩm

---

## ⭐ 7. CẬP NHẬT REVIEW

### Endpoint
```
PUT /api/user/reviews/{reviewId}
```

### Headers
```
Authorization: Bearer <your_token>
Content-Type: multipart/form-data
```

### Body (form-data)

| Key | Type | Required | Description |
|-----|------|----------|-------------|
| `data` | Text | ✅ Yes | JSON string của UpdateReviewRequest |
| `images` | File[] | ❌ No | Ảnh mới (nếu muốn đổi ảnh) |

### Ví dụ JSON cho field `data`:
```json
{
  "rating": 4,
  "comment": "Món ăn ngon nhưng hơi mặn",
  "isAnonymous": false
}
```

---

## 📊 8. XEM ĐÁNH GIÁ THEO SHOP

### Endpoint
```
GET /api/public/reviews/shops/{shopId}
```

### Query Parameters
- `page` (default: 0)
- `size` (default: 10)
- `sortBy` (default: createdAt)
- `sortDir` (default: DESC)

### Ví dụ:
```
GET /api/public/reviews/shops/1?page=0&size=10&sortBy=createdAt&sortDir=DESC
```

### Response:
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "userId": 2,
      "userFullName": "Nguyễn Văn A",
      "userAvatarUrl": "...",
      "reviewableType": "shop",
      "reviewableId": 1,
      "reviewableName": "Quán Ăn Ngon",
      "rating": 5,
      "comment": "Quán ăn rất ngon!",
      "images": ["path1", "path2"],
      "createdAt": "2026-01-18T10:00:00"
    }
  ],
  "currentPage": 0,
  "totalItems": 50,
  "totalPages": 5
}
```

---

## 📊 9. XEM ĐÁNH GIÁ THEO SẢN PHẨM

### Endpoint
```
GET /api/public/reviews/products/{productId}
```

### Query Parameters
- `page` (default: 0)
- `size` (default: 10)
- `sortBy` (default: createdAt)
- `sortDir` (default: DESC)

### Ví dụ:
```
GET /api/public/reviews/products/5?page=0&size=10
```

---

## 📊 10. XEM THỐNG KÊ ĐÁNH GIÁ SHOP

### Endpoint
```
GET /api/public/reviews/shops/{shopId}/statistics
```

### Response:
```json
{
  "success": true,
  "data": {
    "averageRating": 4.5,
    "totalReviews": 100,
    "fiveStarCount": 60,
    "fourStarCount": 25,
    "threeStarCount": 10,
    "twoStarCount": 3,
    "oneStarCount": 2
  }
}
```

---

## 📊 11. XEM THỐNG KÊ ĐÁNH GIÁ SẢN PHẨM

### Endpoint
```
GET /api/public/reviews/products/{productId}/statistics
```

---

## 🎯 LOGIC HOẠT ĐỘNG (Giống Shopee)

### Review Shop:
- Bấm vào shop → Hiện tab "Đánh giá shop"
- Endpoint: `GET /api/public/reviews/shops/{shopId}`
- Hiển thị tất cả đánh giá về shop đó

### Review Product:
- Bấm vào sản phẩm → Cuộn xuống phần "Đánh giá sản phẩm"
- Endpoint: `GET /api/public/reviews/products/{productId}`
- Hiển thị đánh giá về sản phẩm đó từ các đơn hàng đã mua

### Tạo Review Product:
- Vào "Đơn hàng của tôi" → Chọn đơn đã "Hoàn thành"
- Bấm "Đánh giá" trên từng sản phẩm
- Endpoint: `POST /api/user/reviews/products/{productId}/orders/{orderId}`

---

## 🔧 TEST TRONG POSTMAN

### Tạo Shop với Logo & Banner:

1. **Tạo request mới:**
   - Method: POST
   - URL: `http://localhost:8080/api/seller/shops`

2. **Headers:**
   ```
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
   ```

3. **Body → form-data:**
   - Key: `data`, Type: Text
     ```json
     {
       "shopName": "Quán Ăn Ngon",
       "description": "Món ăn địa phương",
       "phone": "0905123456",
       "email": "quan@example.com",
       "businessLicense": "BL-123456",
       "taxCode": "TAX-987654",
       "address": {
         "addressLine": "123 Nguyễn Văn Linh",
         "ward": "Hòa Xuân",
         "district": "Cẩm Lệ",
         "city": "Đà Nẵng",
         "country": "Vietnam",
         "postalCode": "550000",
         "latitude": 16.0544,
         "longitude": 108.2022
       },
       "openingHours": "{\"monday\":{\"open\":\"08:00\",\"close\":\"22:00\"}}"
     }
     ```
   - Key: `logo`, Type: File → Chọn file logo.png
   - Key: `banner`, Type: File → Chọn file banner.jpg

4. **Send!**

---

## 📂 SUBFOLDER ID - GIẢI THÍCH CHI TIẾT

### Tại sao cần subfolder ID?

**TRƯỚC ĐÂY** (không có subfolder):
```
ProductImage/
├── 1234567890-abc123.jpg  ❌ Không biết file này của shop nào?
├── 1234567891-def456.jpg  ❌ Không biết file này của shop nào?
└── 1234567892-ghi789.jpg  ❌ Rất khó quản lý khi có 1000+ files!
```

**BÂY GIỜ** (có subfolder ID):
```
ProductImage/
├── shop_1/
│   ├── 1234567890-abc123.jpg  ✅ Ảnh của shop 1
│   └── 1234567891-def456.jpg  ✅ Ảnh của shop 1
├── shop_2/
│   └── 1234567892-ghi789.jpg  ✅ Ảnh của shop 2
└── product_5/
    └── 1234567893-jkl012.jpg  ✅ Ảnh update của product 5
```

### Quy tắc đặt tên subfolder:

| Loại File | Khi Tạo Mới | Khi Update |
|-----------|-------------|------------|
| **Shop Logo/Banner** | `seller_X` (X = seller ID) | `shop_X` (X = shop ID) |
| **Product Image** | `shop_X` (X = shop ID từ request) | `product_X` (X = product ID) |
| **Review Image** | `user_X` (X = user ID đang login) | `user_X` (X = user ID đang login) |

### Lợi ích thực tế:

1. **Dễ tìm kiếm**: Muốn xem ảnh của shop 5? → Vào `ProductImage/shop_5/`
2. **Dễ xóa**: Muốn xóa tất cả ảnh của shop 5? → Xóa folder `ProductImage/shop_5/`
3. **Dễ backup**: Backup riêng từng shop/user
4. **Dễ debug**: Khi có lỗi, biết ngay file thuộc về ai

---

## ✅ CHECKLIST SỬA CODE

- [x] Cập nhật `FileStorageService` với 6 category mới
- [x] Thêm method `storeFile` có parameter `subfolderId`
- [x] Sửa `SellerShopController` dùng subfolder `seller_X` / `shop_X`
- [x] Sửa `SellerProductController` dùng subfolder `shop_X` / `product_X`
- [x] Sửa `ReviewServiceImpl` dùng subfolder `user_X`
- [x] Endpoint review có `{shopId}` và `{productId}` rõ ràng
- [x] Tạo tài liệu hướng dẫn chi tiết với subfolder ID

---

## 📝 GHI CHÚ

- Tất cả upload đều dùng **form-data**, không còn gửi URL trong JSON
- File được validate: chỉ cho phép JPG, JPEG, PNG (max 5MB)
- File được lưu với tên unique: `timestamp-uuid.extension`
- Mỗi loại file có thư mục riêng với **subfolder ID** để dễ quản lý và phân biệt
- Subfolder được tạo tự động khi upload, không cần tạo thủ công

**Tác giả:** AI Assistant  
**Ngày tạo:** 18/01/2026  
**Cập nhật:** 18/01/2026 (Thêm subfolder ID)
