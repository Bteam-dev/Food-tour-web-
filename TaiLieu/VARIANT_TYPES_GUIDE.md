# Hướng Dẫn Sử Dụng Hệ Thống Variant Types

## Tổng Quan
Hệ thống Variant Types cho phép Admin quản lý tập trung các loại biến thể (như Size, Topping, Màu sắc, v.v.), và Seller chọn các loại này khi tạo sản phẩm.

## Cấu Trúc Mới

### 1. VariantType (Do Admin quản lý)
- **id**: ID của loại biến thể
- **name**: Tên loại (vd: "Size", "Topping", "Đá", "Đường")
- **description**: Mô tả
- **isActive**: Trạng thái active
- **createdAt**, **updatedAt**: Thời gian tạo/cập nhật

### 2. ProductVariant (Do Seller tạo)
- **id**: ID của biến thể
- **productId**: ID sản phẩm
- **variantTypeId**: ID loại biến thể (liên kết với VariantType)
- **variantValue**: Giá trị cụ thể (vd: "Lớn", "Phô mai", "100%")
- **priceAdjustment**: Điều chỉnh giá
- **isActive**: Trạng thái

---

## API Endpoints

### A. API cho ADMIN - Quản lý Variant Types

#### 1. Lấy tất cả variant types
```
GET /api/admin/variant-types
Authorization: Bearer {admin_token}
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Size",
    "description": "Kích thước sản phẩm",
    "isActive": true,
    "createdAt": "2025-10-22T10:00:00",
    "updatedAt": "2025-10-22T10:00:00"
  }
]
```

#### 2. Tạo variant type mới
```
POST /api/admin/variant-types
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Topping",
  "description": "Các loại topping thêm vào món ăn"
}
```

#### 3. Cập nhật variant type
```
PUT /api/admin/variant-types/{id}
Authorization: Bearer {admin_token}
Content-Type: application/json

{
  "name": "Size",
  "description": "Kích thước (Nhỏ, Vừa, Lớn, XL)"
}
```

#### 4. Xóa mềm variant type
```
DELETE /api/admin/variant-types/{id}
Authorization: Bearer {admin_token}
```

#### 5. Kích hoạt lại variant type
```
PATCH /api/admin/variant-types/{id}/activate
Authorization: Bearer {admin_token}
```

---

### B. API cho PUBLIC/SELLER - Xem Variant Types

#### 1. Lấy danh sách variant types đang active
```
GET /api/public/variant-types
```

**Response:**
```json
[
  {
    "id": 1,
    "name": "Size",
    "description": "Kích thước sản phẩm (Nhỏ, Vừa, Lớn)",
    "isActive": true,
    "createdAt": "2025-10-22T10:00:00",
    "updatedAt": "2025-10-22T10:00:00"
  },
  {
    "id": 2,
    "name": "Topping",
    "description": "Các loại topping thêm vào món ăn",
    "isActive": true,
    "createdAt": "2025-10-22T10:00:00",
    "updatedAt": "2025-10-22T10:00:00"
  }
]
```

---

### C. API cho SELLER - Tạo Product với Variants

#### 1. Tạo sản phẩm mới với variants
```
POST /api/seller/products
Authorization: Bearer {seller_token}
Content-Type: application/json

{
  "shopId": 1,
  "categoryId": 2,
  "name": "Pizza Hải Sản",
  "description": "Pizza với hải sản tươi ngon",
  "price": 150000,
  "discountPrice": 135000,
  "imageUrls": ["url1", "url2"],
  "variants": [
    {
      "variantTypeId": 1,  // Size
      "variantValue": "Nhỏ (6 inch)",
      "priceAdjustment": 0
    },
    {
      "variantTypeId": 1,  // Size
      "variantValue": "Vừa (9 inch)",
      "priceAdjustment": 30000
    },
    {
      "variantTypeId": 1,  // Size
      "variantValue": "Lớn (12 inch)",
      "priceAdjustment": 60000
    },
    {
      "variantTypeId": 2,  // Topping
      "variantValue": "Phô mai thêm",
      "priceAdjustment": 15000
    },
    {
      "variantTypeId": 2,  // Topping
      "variantValue": "Pepperoni",
      "priceAdjustment": 20000
    }
  ]
}
```

**Response:**
```json
{
  "id": 10,
  "name": "Pizza Hải Sản",
  "price": 150000,
  "variants": [
    {
      "id": 1,
      "variantTypeId": 1,
      "variantTypeName": "Size",
      "variantValue": "Nhỏ (6 inch)",
      "priceAdjustment": 0,
      "isActive": true
    },
    {
      "id": 2,
      "variantTypeId": 1,
      "variantTypeName": "Size",
      "variantValue": "Vừa (9 inch)",
      "priceAdjustment": 30000,
      "isActive": true
    },
    {
      "id": 3,
      "variantTypeId": 1,
      "variantTypeName": "Size",
      "variantValue": "Lớn (12 inch)",
      "priceAdjustment": 60000,
      "isActive": true
    },
    {
      "id": 4,
      "variantTypeId": 2,
      "variantTypeName": "Topping",
      "variantValue": "Phô mai thêm",
      "priceAdjustment": 15000,
      "isActive": true
    },
    {
      "id": 5,
      "variantTypeId": 2,
      "variantTypeName": "Topping",
      "variantValue": "Pepperoni",
      "priceAdjustment": 20000,
      "isActive": true
    }
  ]
}
```

#### 2. Thêm variant cho sản phẩm có sẵn
```
POST /api/seller/products/{productId}/variants
Authorization: Bearer {seller_token}
Content-Type: application/json

{
  "variantTypeId": 2,  // Topping
  "variantValue": "Thịt nguội",
  "priceAdjustment": 18000
}
```

#### 3. Cập nhật variant
```
PUT /api/seller/variants/{variantId}
Authorization: Bearer {seller_token}
Content-Type: application/json

{
  "variantTypeId": 2,
  "variantValue": "Thịt nguội cao cấp",
  "priceAdjustment": 25000,
  "isActive": true
}
```

---

## Workflow Sử Dụng

### Bước 1: Admin thiết lập Variant Types
```
1. Admin tạo các loại biến thể:
   - Size (Kích thước)
   - Topping (Nguyên liệu thêm)
   - Đá (Mức độ đá)
   - Đường (Mức độ đường)
   - Màu sắc
   - v.v.
```

### Bước 2: Seller tạo sản phẩm
```
1. Seller lấy danh sách variant types: GET /api/public/variant-types
2. Seller chọn variant types phù hợp cho sản phẩm
3. Seller tạo các variant values cụ thể:
   - Chọn "Size" → thêm: Nhỏ, Vừa, Lớn
   - Chọn "Topping" → thêm: Phô mai, Pepperoni, Thịt nguội
4. Seller tạo sản phẩm với các variants đã chọn
```

### Bước 3: Customer xem và chọn
```
1. Customer xem sản phẩm, thấy các options:
   - Size: Nhỏ (+0đ), Vừa (+30k), Lớn (+60k)
   - Topping: Phô mai (+15k), Pepperoni (+20k)
2. Customer chọn: Size Lớn + Topping Pepperoni
3. Tổng giá = 150,000 + 60,000 + 20,000 = 230,000đ
```

---

## Ví Dụ Thực Tế

### Ví dụ 1: Quán Pizza
```json
Variant Types:
- Size: Nhỏ (6"), Vừa (9"), Lớn (12"), XL (15")
- Topping: Phô mai, Pepperoni, Thịt nguội, Nấm, Ớt chuông
- Viền: Viền phô mai, Viền xúc xích
```

### Ví dụ 2: Quán Trà Sữa
```json
Variant Types:
- Size: M, L, XL
- Đường: 0%, 30%, 50%, 70%, 100%
- Đá: Không đá, Ít đá, Đá bình thường
- Topping: Trân châu, Thạch, Pudding, Kem cheese
```

### Ví dụ 3: Quán Cơm
```json
Variant Types:
- Size: Nhỏ, Vừa, Lớn
- Thêm món: Trứng chiên, Thêm rau, Thêm thịt
```

---

## Migration Database

Chạy file SQL sau để migrate database:
```
TaiLieu/variant_types_migration.sql
```

Script này sẽ:
1. Tạo bảng `variant_types`
2. Thêm dữ liệu mẫu cho variant types
3. Cập nhật bảng `product_variants` với cấu trúc mới
4. Migration dữ liệu cũ (nếu có)
5. Tạo indexes để tối ưu

---

## Lưu Ý

1. **Admin phải tạo Variant Types trước** khi Seller có thể sử dụng
2. **Variant Types không thể xóa cứng** nếu đang được sử dụng bởi products
3. Seller **chỉ có thể chọn** các Variant Types đã được Admin tạo
4. Mỗi product có thể có **nhiều variants** với **nhiều variant types** khác nhau
5. Price adjustment có thể **âm** (giảm giá) hoặc **dương** (tăng giá)

---

## Các Files Đã Tạo/Cập Nhật

### Entities:
- `VariantType.java` - Entity mới cho loại biến thể
- `ProductVariant.java` - Cập nhật để liên kết với VariantType

### Repositories:
- `VariantTypeRepository.java` - Repository cho VariantType

### DTOs:
- `VariantTypeRequestDTO.java` - Request DTO
- `VariantTypeResponseDTO.java` - Response DTO
- `CreateVariantRequestDTO.java` - Cập nhật để dùng variantTypeId
- `UpdateVariantRequestDTO.java` - Cập nhật để dùng variantTypeId
- `VariantResponseDTO.java` - Cập nhật để trả về thông tin variant type

### Services:
- `VariantTypeService.java` - Service mới cho VariantType
- `ProductServiceImpl.java` - Cập nhật để xử lý VariantType

### Controllers:
- `AdminVariantTypeController.java` - API cho Admin quản lý
- `PublicVariantTypeController.java` - API public để xem danh sách

### Migration:
- `variant_types_migration.sql` - Script SQL để migrate database

