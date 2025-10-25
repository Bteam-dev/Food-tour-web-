# API Endpoints cho Seller

## Tổng quan
Tài liệu này mô tả các API endpoints dành cho Seller để quản lý Shop và Product.

---

## 🏪 QUẢN LÝ SHOP (SellerShopController)

### 1. Tạo cửa hàng mới
**POST** `/api/seller/shops`

**Mô tả:** Seller tạo cửa hàng mới, có thể upload logo và banner

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: multipart/form-data` (nếu có upload ảnh)
- `Content-Type: application/json` (nếu không có ảnh)

**Form data (khi có ảnh):**
- `data`: JSON string (CreateShopRequestDTO)
- `logo`: file (optional, jpg/jpeg/png, max 5MB)
- `banner`: file (optional, jpg/jpeg/png, max 5MB)

**Response:** ShopResponseDTO

---

### 2. Lấy danh sách shop của seller
**GET** `/api/seller/shops/my-shops`

**Mô tả:** Lấy tất cả shop mà seller đã tạo

**Headers:**
- `Authorization: Bearer {token}`

**Response:** 
```json
[
  {
    "id": 1,
    "name": "Quán Ăn ABC",
    "description": "...",
    "sellerId": 10,
    "logoUrl": "...",
    "bannerUrl": "...",
    "isActive": true,
    ...
  }
]
```

---

### 3. Xem chi tiết shop
**GET** `/api/seller/shops/{shopId}`

**Mô tả:** Xem thông tin chi tiết một shop của seller (hoặc admin xem bất kỳ shop nào)

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `shopId`: ID của shop cần xem

**Response:** ShopResponseDTO

**Lưu ý:** 
- Seller chỉ được xem shop của chính mình
- Admin có thể xem mọi shop

---

### 4. Cập nhật thông tin shop
**PUT** `/api/seller/shops/{shopId}`

**Mô tả:** Cập nhật thông tin shop, có thể upload logo/banner mới

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: multipart/form-data` (nếu có upload ảnh)

**Path Parameters:**
- `shopId`: ID của shop cần cập nhật

**Form data:**
- `data`: JSON string (UpdateShopRequestDTO)
- `logo`: file (optional)
- `banner`: file (optional)

**Response:** ShopResponseDTO

---

### 5. Xóa shop
**DELETE** `/api/seller/shops/{shopId}`

**Mô tả:** Xóa shop (soft delete - set isActive = false)

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `shopId`: ID của shop cần xóa

**Response:** 204 No Content

---

## 🍔 QUẢN LÝ SẢN PHẨM (SellerProductController)

### 1. Tạo sản phẩm mới
**POST** `/api/seller/products`

**Mô tả:** Tạo sản phẩm mới, có thể upload nhiều ảnh

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: multipart/form-data` (nếu có upload ảnh)

**Form data:**
- `data`: JSON string (CreateProductRequestDTO)
- `images`: multiple files (có thể chọn nhiều ảnh, jpg/jpeg/png, max 5MB each)

**Response:**
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "name": "Phở bò",
    "shopId": 5,
    "categoryId": 2,
    "imageUrls": ["url1", "url2"],
    "variants": [...]
  }
}
```

---

### 2. Xem chi tiết sản phẩm
**GET** `/api/seller/products/{id}`

**Mô tả:** Xem thông tin chi tiết sản phẩm (bao gồm cả variants)

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `id`: ID của sản phẩm

**Response:**
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "Phở bò",
    "description": "...",
    "shopId": 5,
    "categoryId": 2,
    "imageUrls": ["url1", "url2"],
    "isAvailable": true,
    "variants": [
      {
        "id": 1,
        "name": "Size nhỏ",
        "price": 35000,
        "stock": 100
      }
    ]
  }
}
```

**Lưu ý:**
- Seller chỉ xem được sản phẩm của shop mình
- Admin xem được mọi sản phẩm

---

### 3. Lấy danh sách sản phẩm theo shop
**GET** `/api/seller/products/shop/{shopId}`

**Mô tả:** Lấy tất cả sản phẩm của một shop cụ thể

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `shopId`: ID của shop

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Phở bò",
      ...
    },
    {
      "id": 2,
      "name": "Bún chả",
      ...
    }
  ]
}
```

---

### 4. Lấy tất cả sản phẩm của seller
**GET** `/api/seller/products/my-products`

**Mô tả:** Lấy tất cả sản phẩm của tất cả shop mà seller sở hữu

**Headers:**
- `Authorization: Bearer {token}`

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "name": "Phở bò",
      "shopId": 5,
      ...
    },
    {
      "id": 2,
      "name": "Bún chả",
      "shopId": 5,
      ...
    },
    {
      "id": 3,
      "name": "Cơm tấm",
      "shopId": 8,
      ...
    }
  ]
}
```

---

### 5. Cập nhật sản phẩm
**PUT** `/api/seller/products/{id}`

**Mô tả:** Cập nhật thông tin sản phẩm, có thể upload ảnh mới

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: multipart/form-data` (nếu có upload ảnh)

**Path Parameters:**
- `id`: ID của sản phẩm

**Form data:**
- `data`: JSON string (UpdateProductRequestDTO)
- `images`: multiple files (optional, sẽ thay thế ảnh cũ)

**Response:**
```json
{
  "success": true,
  "message": "Product updated successfully",
  "data": { ... }
}
```

---

### 6. Xóa sản phẩm
**DELETE** `/api/seller/products/{id}`

**Mô tả:** Xóa sản phẩm (soft delete - set isAvailable = false)

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `id`: ID của sản phẩm

**Response:**
```json
{
  "success": true,
  "message": "Product deleted successfully"
}
```

---

## 🎨 QUẢN LÝ VARIANT (Product Variants)

### 7. Thêm variant cho sản phẩm
**POST** `/api/seller/products/{productId}/variants`

**Mô tả:** Thêm một variant mới cho sản phẩm (ví dụ: size nhỏ, size lớn)

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: application/json`

**Path Parameters:**
- `productId`: ID của sản phẩm

**Request Body:**
```json
{
  "name": "Size lớn",
  "price": 45000,
  "stock": 50,
  "sku": "PHO-L"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Variant added successfully",
  "data": {
    "id": 2,
    "name": "Size lớn",
    "price": 45000,
    "stock": 50
  }
}
```

---

### 8. Cập nhật variant
**PUT** `/api/seller/products/{productId}/variants/{variantId}`

**Mô tả:** Cập nhật thông tin variant

**Headers:**
- `Authorization: Bearer {token}`
- `Content-Type: application/json`

**Path Parameters:**
- `productId`: ID của sản phẩm
- `variantId`: ID của variant

**Request Body:**
```json
{
  "name": "Size lớn (cập nhật)",
  "price": 50000,
  "stock": 30
}
```

**Response:**
```json
{
  "success": true,
  "message": "Variant updated successfully",
  "data": { ... }
}
```

---

### 9. Xóa variant
**DELETE** `/api/seller/products/{productId}/variants/{variantId}`

**Mô tả:** Xóa variant (soft delete)

**Headers:**
- `Authorization: Bearer {token}`

**Path Parameters:**
- `productId`: ID của sản phẩm
- `variantId`: ID của variant

**Response:**
```json
{
  "success": true,
  "message": "Variant deleted successfully"
}
```

---

## 📋 Quyền truy cập

### Seller (ROLE_SELLER):
- ✅ Tạo, xem, sửa, xóa shop của chính mình
- ✅ Tạo, xem, sửa, xóa sản phẩm của shop mình
- ✅ Quản lý variants của sản phẩm mình
- ❌ KHÔNG thể truy cập shop/sản phẩm của seller khác

### Admin (ROLE_ADMIN):
- ✅ Tất cả quyền của Seller
- ✅ Xem, sửa, xóa mọi shop
- ✅ Xem, sửa, xóa mọi sản phẩm
- ✅ Endpoint riêng: GET `/api/seller/products/all` (xem tất cả sản phẩm kể cả inactive)

---

## 💡 Lưu ý quan trọng

1. **Upload ảnh:**
   - Khi có ảnh: dùng `multipart/form-data`
   - Khi không có ảnh: dùng `application/json`
   - Data phải là JSON string khi dùng multipart

2. **Authentication:**
   - Tất cả endpoints đều cần JWT token
   - Token phải có role SELLER hoặc ADMIN

3. **Soft Delete:**
   - Xóa shop/sản phẩm chỉ set `isActive = false`
   - Dữ liệu vẫn còn trong database

4. **Kiểm tra quyền:**
   - Mỗi endpoint đều kiểm tra quyền sở hữu
   - Admin có thể bypass các kiểm tra

---

## 🔍 Flow sử dụng điển hình

### Seller mới bắt đầu:

1. **Tạo shop:** POST `/api/seller/shops`
2. **Xem shop vừa tạo:** GET `/api/seller/shops/my-shops`
3. **Tạo sản phẩm:** POST `/api/seller/products` (với shopId từ bước 1)
4. **Thêm variants:** POST `/api/seller/products/{productId}/variants`
5. **Xem tất cả sản phẩm:** GET `/api/seller/products/my-products`
6. **Xem chi tiết sản phẩm:** GET `/api/seller/products/{id}`
7. **Cập nhật khi cần:** PUT `/api/seller/products/{id}`

### Seller quản lý nhiều shop:

1. **Xem tất cả shop:** GET `/api/seller/shops/my-shops`
2. **Xem sản phẩm theo shop:** GET `/api/seller/products/shop/{shopId}`
3. **Xem tất cả sản phẩm:** GET `/api/seller/products/my-products`

---

Mọi thắc mắc xin liên hệ team Backend! 🚀

