# Hướng Dẫn Sử Dụng Postman - API Tạo Shop

## 🔐 Bước 1: Lấy Token (Bắt buộc)

Trước khi gọi API tạo shop, bạn cần đăng nhập để lấy JWT token:

### Login Request
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json
```

**Body:**
```json
{
  "email": "anhdaycotai196@gmail.com",
  "password": "your_password"
}
```

**Lưu lại token từ response để dùng cho các request sau!**

---

## 📝 Bước 2A: Tạo Shop - KHÔNG CÓ ẢNH (JSON thuần)

### Request Info
```
POST http://localhost:8080/api/seller/shops
Content-Type: application/json
Authorization: Bearer YOUR_TOKEN_HERE
```

### Cách cấu hình trong Postman:

1. **Method**: `POST`
2. **URL**: `http://localhost:8080/api/seller/shops`
3. **Headers**:
   - Key: `Content-Type` → Value: `application/json`
   - Key: `Authorization` → Value: `Bearer YOUR_TOKEN_HERE`
4. **Body**: 
   - Chọn tab **"Body"**
   - Chọn **"raw"**
   - Chọn **"JSON"** (dropdown bên phải)
   - Paste JSON bên dưới:

### Body JSON:
```json
{
  "shopName": "Quán Ăn Ngon",
  "description": "Món ăn địa phương đặc sản Đà Nẵng",
  "phone": "0905123456",
  "email": "quan@example.com",
  "businessLicense": "BL-123456",
  "taxCode": "TAX-987654",
  "logoUrl": "https://example.com/logo.png",
  "bannerUrl": "https://example.com/banner.png",
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
  "openingHours": "{\"monday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"tuesday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"wednesday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"thursday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"friday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"saturday\":{\"open\":\"08:00\",\"close\":\"23:00\"},\"sunday\":{\"open\":\"08:00\",\"close\":\"23:00\"}}"
}
```

### Expected Response (201 Created):
```json
{
  "id": 1,
  "shopName": "Quán Ăn Ngon",
  "description": "Món ăn địa phương đặc sản Đà Nẵng",
  "logoUrl": "https://example.com/logo.png",
  "bannerUrl": "https://example.com/banner.png",
  "status": "PENDING",
  "createdAt": "2026-01-16T22:46:20",
  ...
}
```

---

## 📷 Bước 2B: Tạo Shop - CÓ UPLOAD ẢNH (Multipart Form-Data)

**⚠️ LƯU Ý: Phải sửa lại code Controller trước khi dùng cách này!**

### Request Info
```
POST http://localhost:8080/api/seller/shops
Content-Type: multipart/form-data
Authorization: Bearer YOUR_TOKEN_HERE
```

### Cách cấu hình trong Postman:

1. **Method**: `POST`
2. **URL**: `http://localhost:8080/api/seller/shops`
3. **Headers**:
   - Key: `Authorization` → Value: `Bearer YOUR_TOKEN_HERE`
   - ⚠️ **KHÔNG** set `Content-Type` (Postman tự động set khi dùng form-data)
4. **Body**:
   - Chọn tab **"Body"**
   - Chọn **"form-data"**
   - Thêm các field sau:

### Form-Data Fields:

| Key | Type | Value |
|-----|------|-------|
| `data` | Text | (Paste JSON bên dưới) |
| `logo` | File | (Chọn file ảnh từ máy) |
| `banner` | File | (Chọn file ảnh từ máy) |

### JSON cho field "data":
```json
{
  "shopName": "Quán Ăn Ngon",
  "description": "Món ăn địa phương đặc sản Đà Nẵng",
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
  "openingHours": "{\"monday\":{\"open\":\"08:00\",\"close\":\"22:00\"},\"tuesday\":{\"open\":\"08:00\",\"close\":\"22:00\"}}"
}
```

**⚠️ Chú ý**: 
- Không cần điền `logoUrl` và `bannerUrl` trong JSON khi upload file
- File ảnh sẽ được tự động upload và URL sẽ được tạo tự động
- File hỗ trợ: JPG, JPEG, PNG (max 5MB)

---

## 🔄 Bước 3: Cập Nhật Shop (Update)

### A. Không đổi ảnh (JSON)
```
PUT http://localhost:8080/api/seller/shops/{shopId}
Content-Type: application/json
Authorization: Bearer YOUR_TOKEN_HERE
```

**Body tương tự như Create, nhưng tất cả field đều optional**

### B. Có đổi ảnh (Form-Data)
```
PUT http://localhost:8080/api/seller/shops/{shopId}
Content-Type: multipart/form-data
Authorization: Bearer YOUR_TOKEN_HERE
```

**Cấu hình giống như Create với form-data**

---

## 📋 Bước 4: Lấy Danh Sách Shop Của Mình

```
GET http://localhost:8080/api/seller/shops/my-shops
Authorization: Bearer YOUR_TOKEN_HERE
```

**Headers:**
- `Authorization`: `Bearer YOUR_TOKEN_HERE`

**No Body needed**

---

## 🗑️ Bước 5: Xóa Shop (Soft Delete)

```
DELETE http://localhost:8080/api/seller/shops/{shopId}
Authorization: Bearer YOUR_TOKEN_HERE
```

**Headers:**
- `Authorization`: `Bearer YOUR_TOKEN_HERE`

**No Body needed**

---

## ⚠️ Lỗi Thường Gặp

### 1. "Thiếu dữ liệu shop"
**Nguyên nhân**: 
- Gửi JSON nhưng code hiện tại chỉ nhận `@RequestParam("data")`
- **Giải pháp**: Dùng form-data với key "data" HOẶC sửa code Controller (đã được fix ở file mới)

### 2. "401 Unauthorized"
**Nguyên nhân**: Thiếu hoặc sai token
**Giải pháp**: 
- Kiểm tra Header `Authorization: Bearer YOUR_TOKEN`
- Token có thể hết hạn, cần login lại

### 3. "403 Forbidden"
**Nguyên nhân**: User không có role SELLER
**Giải pháp**: Kiểm tra role trong database (phải là SELLER hoặc ADMIN)

### 4. Validation Errors
**Nguyên nhân**: Thiếu field bắt buộc
**Required fields:**
- `shopName`
- `businessLicense`
- `taxCode`
- `address` (toàn bộ object)

---

## 💡 Tips

1. **Save Request trong Postman Collection** để dùng lại
2. **Dùng Environment Variables** cho:
   - `{{baseUrl}}` = `http://localhost:8080`
   - `{{token}}` = Bearer token (tự động update sau login)
3. **Test với Postman Tests script** để tự động lưu token:

```javascript
// Trong tab "Tests" của Login request
pm.test("Login successful", function () {
    var jsonData = pm.response.json();
    pm.environment.set("token", jsonData.token);
});
```

Sau đó dùng `{{token}}` trong Header thay vì copy-paste manual!

---

## 📚 Tài Liệu Liên Quan

- [FLOW_TAO_SHOP_VOI_HERE_API.md](./FLOW_TAO_SHOP_VOI_HERE_API.md) - Hướng dẫn tích hợp HERE API
- [SELLER_API_ENDPOINTS.md](./SELLER_API_ENDPOINTS.md) - Tất cả API cho Seller

---

**Created**: 2026-01-16  
**Last Updated**: 2026-01-16

