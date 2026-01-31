# HƯỚNG DẪN QUẢN LÝ SẢN PHẨM THEO SHOP

## 📋 Tổng quan

**Thay đổi mới**: Thay vì phải điền `shopId` trong request body khi tạo/quản lý sản phẩm, giờ đây:
1. Seller chọn shop từ danh sách shop của mình
2. Vào trang quản lý của shop cụ thể đó
3. Tất cả thao tác quản lý sản phẩm sẽ tự động gắn với shopId trong URL

## 🔄 So sánh API cũ vs mới

### API CŨ (vẫn hoạt động):
```
POST /api/seller/products
Body: {
  "shopId": 123,  // ❌ Phải điền thủ công
  "name": "Phở bò",
  ...
}
```

### API MỚI (khuyến nghị):
```
POST /api/seller/shops/123/products
Body: {
  // ✅ Không cần điền shopId nữa
  "name": "Phở bò",
  ...
}
```

---

## 📡 API Endpoints

### 1️⃣ Lấy danh sách shop của seller

**Endpoint**: `GET /api/seller/shops/my-shops`

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 123,
      "name": "Quán Phở Hà Nội",
      "logoUrl": "...",
      "address": "..."
    },
    {
      "id": 456,
      "name": "Quán Cơm Tấm",
      ...
    }
  ]
}
```

**Frontend flow**:
- Gọi API này để hiển thị danh sách shop
- User chọn 1 shop → lưu `shopId`
- Chuyển sang trang quản lý shop với `shopId` trong URL/state

---

### 2️⃣ Lấy danh sách sản phẩm của shop (có phân trang)

**Endpoint**: `GET /api/seller/shops/{shopId}/products?page=0&size=10&sortBy=createdAt&direction=DESC`

**Params**:
- `page`: số trang (mặc định 0)
- `size`: số item/trang (mặc định 10)
- `sortBy`: sắp xếp theo field (mặc định `createdAt`)
- `direction`: `ASC` hoặc `DESC` (mặc định `DESC`)

**Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": 789,
      "name": "Phở bò đặc biệt",
      "price": 50000,
      "shopId": 123,
      ...
    }
  ],
  "currentPage": 0,
  "totalItems": 25,
  "totalPages": 3
}
```

---

### 3️⃣ Tạo sản phẩm mới cho shop

**Endpoint**: `POST /api/seller/shops/{shopId}/products`

**Content-Type**: `multipart/form-data`

**Form Fields**:
- `data` (JSON string): thông tin sản phẩm (KHÔNG CẦN shopId)
- `images` (files[]): ảnh sản phẩm (tùy chọn, max 5MB/file)

**Example `data` field**:
```json
{
  "name": "Phở bò đặc biệt",
  "description": "Phở bò truyền thống",
  "price": 50000,
  "categoryId": 1,
  "stockQuantity": 100,
  "preparationTime": 15,
  "variants": [
    {
      "variantTypeId": 1,
      "name": "Size lớn",
      "additionalPrice": 10000
    }
  ]
}
```

**Response**:
```json
{
  "success": true,
  "message": "Sản phẩm đã được tạo thành công",
  "data": {
    "id": 789,
    "name": "Phở bò đặc biệt",
    "shopId": 123,
    "imageUrls": ["https://..."],
    ...
  }
}
```

---

### 4️⃣ Lấy chi tiết sản phẩm

**Endpoint**: `GET /api/seller/shops/{shopId}/products/{productId}`

**Response**:
```json
{
  "success": true,
  "data": {
    "id": 789,
    "name": "Phở bò đặc biệt",
    "shopId": 123,
    "variants": [...],
    ...
  }
}
```

---

### 5️⃣ Cập nhật sản phẩm

**Endpoint**: `PUT /api/seller/shops/{shopId}/products/{productId}`

**Content-Type**: `multipart/form-data`

**Form Fields**:
- `data` (JSON string): thông tin cập nhật
- `images` (files[]): ảnh mới (tùy chọn, sẽ thay thế ảnh cũ)

**Example `data` field**:
```json
{
  "name": "Phở bò đặc biệt - Updated",
  "price": 55000,
  "isAvailable": true
}
```

---

### 6️⃣ Xóa sản phẩm (soft delete)

**Endpoint**: `DELETE /api/seller/shops/{shopId}/products/{productId}`

**Response**:
```json
{
  "success": true,
  "message": "Sản phẩm đã được xóa"
}
```

---

### 7️⃣ Quản lý Variants

#### Thêm variant
**Endpoint**: `POST /api/seller/shops/{shopId}/products/{productId}/variants`

**Body**:
```json
{
  "variantTypeId": 1,
  "name": "Size vừa",
  "additionalPrice": 5000,
  "stockQuantity": 50
}
```

#### Cập nhật variant
**Endpoint**: `PUT /api/seller/shops/{shopId}/products/{productId}/variants/{variantId}`

**Body**:
```json
{
  "name": "Size vừa - Updated",
  "additionalPrice": 7000
}
```

#### Xóa variant
**Endpoint**: `DELETE /api/seller/shops/{shopId}/products/{productId}/variants/{variantId}`

---

## 🔐 Bảo mật

- ✅ Tự động validate quyền sở hữu shop
- ✅ Seller chỉ có thể truy cập shop của mình
- ✅ Admin có thể truy cập tất cả shop
- ✅ Tự động kiểm tra sản phẩm có thuộc shop không

---

## 🎨 Frontend Integration Guide

### React/React Native Example:

```javascript
// 1. Lấy danh sách shop
const shops = await api.get('/api/seller/shops/my-shops');

// 2. User chọn shop
const selectedShopId = 123;

// 3. Lấy sản phẩm của shop
const products = await api.get(`/api/seller/shops/${selectedShopId}/products?page=0&size=10`);

// 4. Tạo sản phẩm mới (không cần điền shopId)
const formData = new FormData();
formData.append('data', JSON.stringify({
  name: 'Phở bò',
  price: 50000,
  categoryId: 1
}));
formData.append('images', imageFile1);
formData.append('images', imageFile2);

const result = await api.post(`/api/seller/shops/${selectedShopId}/products`, formData, {
  headers: { 'Content-Type': 'multipart/form-data' }
});
```

### Kotlin/Android Example:

```kotlin
// 1. Lấy danh sách shop
val shops = apiService.getMyShops()

// 2. User chọn shop
val selectedShopId = 123

// 3. Lấy sản phẩm của shop
val products = apiService.getShopProducts(
    shopId = selectedShopId,
    page = 0,
    size = 10
)

// 4. Tạo sản phẩm mới
val productData = CreateProductRequest(
    name = "Phở bò",
    price = 50000,
    categoryId = 1
)
val requestBody = MultipartBody.Part.createFormData(
    "data", 
    Gson().toJson(productData)
)
val result = apiService.createShopProduct(selectedShopId, requestBody, imagesParts)
```

---

## 📝 Notes

1. **API cũ vẫn hoạt động**: `/api/seller/products` vẫn có thể dùng nếu cần
2. **Khuyến nghị**: Dùng API mới để code rõ ràng và an toàn hơn
3. **Phân trang**: Tất cả danh sách đều có phân trang để tối ưu performance
4. **Upload ảnh**: Dùng `multipart/form-data`, không cần base64

---

## 🐛 Error Handling

### Lỗi thường gặp:

1. **Shop không tồn tại**:
```json
{
  "success": false,
  "message": "Shop không tồn tại"
}
```

2. **Không có quyền truy cập**:
```json
{
  "success": false,
  "message": "Bạn không có quyền truy cập shop này"
}
```

3. **Sản phẩm không thuộc shop**:
```json
{
  "success": false,
  "message": "Sản phẩm không thuộc shop này"
}
```

---

## ✅ Checklist cho Frontend Developer

- [ ] Gọi API lấy danh sách shop của seller
- [ ] Hiển thị danh sách shop dạng list/grid
- [ ] User click chọn shop → lưu shopId
- [ ] Chuyển sang màn hình quản lý shop với shopId
- [ ] Gọi API lấy sản phẩm: `GET /api/seller/shops/{shopId}/products`
- [ ] Form tạo sản phẩm: KHÔNG CẦN điền shopId
- [ ] Submit form: `POST /api/seller/shops/{shopId}/products`
- [ ] Thêm phân trang (page, size params)

---

**Tạo bởi**: Backend Team  
**Ngày tạo**: 2026-01-28  
**Version**: 1.0

