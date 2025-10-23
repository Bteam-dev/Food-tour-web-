# HƯỚNG DẪN UPLOAD ẢNH CHO SHOP VÀ PRODUCT
**Giống như đăng bài trên Facebook - Tất cả trong 1 form duy nhất!** 📸

## Tổng Quan
Hệ thống hoạt động **GIỐNG FACEBOOK**:
- Khi tạo Shop/Product, bạn điền thông tin + có thể chọn ảnh từ máy
- Ảnh tự động upload lên server và lưu URL vào database
- **1 endpoint duy nhất** cho cả có ảnh và không có ảnh!

---

## 🏪 TẠO SHOP VỚI UPLOAD ẢNH (Giống đăng bài Facebook)

### Endpoint
```
POST /api/seller/shops
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

### Cách sử dụng với Postman:

1. **Method**: POST
2. **URL**: `http://localhost:8080/api/seller/shops`
3. **Headers**: 
   - `Authorization: Bearer YOUR_TOKEN`
4. **Body → form-data**:

| Key | Type | Value |
|-----|------|-------|
| `data` | Text | JSON string (xem bên dưới) |
| `logo` | File | Chọn file ảnh logo (optional) |
| `banner` | File | Chọn file ảnh banner (optional) |

**JSON cho field "data":**
```json
{
  "shopName": "Quán Ăn Ngon",
  "description": "Món ăn địa phương đặc sắc",
  "phone": "0905123456",
  "email": "quan@example.com",
  "businessLicense": "GP001234",
  "taxCode": "TAX001234",
  "openingHours": "{\"monday\": {\"open\": \"08:00\", \"close\": \"22:00\"}}",
  "address": {
    "addressLine": "123 Nguyễn Văn Linh",
    "ward": "Hòa Xuân",
    "district": "Cẩm Lệ",
    "city": "Đà Nẵng",
    "country": "Vietnam",
    "postalCode": "550000",
    "latitude": 16.0544,
    "longitude": 108.2022
  }
}
```

### Response:
```json
{
  "id": 1,
  "shopName": "Quán Ăn Ngon",
  "logoUrl": "/uploads/shops/123/1234567890-uuid.jpg",
  "bannerUrl": "/uploads/shops/123/1234567891-uuid.jpg",
  ...
}
```

> ✅ **Ảnh đã được lưu vào database!** URL có sẵn trong response.

---

## 🍜 TẠO PRODUCT VỚI NHIỀU ẢNH (Giống đăng ảnh Facebook)

### Endpoint
```
POST /api/seller/products
Content-Type: multipart/form-data
Authorization: Bearer <token>
```

### Cách sử dụng với Postman:

1. **Method**: POST
2. **URL**: `http://localhost:8080/api/seller/products`
3. **Headers**: 
   - `Authorization: Bearer YOUR_TOKEN`
4. **Body → form-data**:

| Key | Type | Value |
|-----|------|-------|
| `data` | Text | JSON string (xem bên dưới) |
| `images` | File | Chọn ảnh 1 |
| `images` | File | Chọn ảnh 2 |
| `images` | File | Chọn ảnh 3 |
| ... | ... | (có thể chọn nhiều ảnh) |

> 💡 **Tip**: Trong Postman, bạn có thể thêm nhiều dòng cùng key `images` để upload nhiều ảnh!

**JSON cho field "data":**
```json
{
  "shopId": 1,
  "categoryId": 1,
  "name": "Phở Bò Đặc Biệt",
  "description": "Phở bò với thịt bò tươi ngon",
  "price": 45000,
  "discountPrice": 40000,
  "ingredients": "Thịt bò, bánh phở, hành, ngò",
  "nutritionInfo": "Calories: 350, Protein: 25g",
  "preparationTime": 15,
  "stockQuantity": 100,
  "minOrderQuantity": 1,
  "maxOrderQuantity": 10,
  "tags": ["phở", "món bò", "bestseller"]
}
```

### Response:
```json
{
  "success": true,
  "message": "Product created successfully",
  "data": {
    "id": 1,
    "name": "Phở Bò Đặc Biệt",
    "imageUrls": [
      "/uploads/products/123/1234567890-uuid.jpg",
      "/uploads/products/123/1234567891-uuid.jpg",
      "/uploads/products/123/1234567892-uuid.jpg"
    ],
    ...
  }
}
```

> ✅ **Tất cả ảnh đã được lưu vào database!**

---

## 🔄 CẬP NHẬT SHOP VỚI ẢNH MỚI

### Endpoint
```
PUT /api/seller/shops/{shopId}
Content-Type: multipart/form-data
```

**Giống hệt như tạo mới**, chỉ khác là method PUT và có `{shopId}` trong URL.

---

## 🔄 CẬP NHẬT PRODUCT VỚI ẢNH MỚI

### Endpoint
```
PUT /api/seller/products/{productId}
Content-Type: multipart/form-data
```

**Giống hệt như tạo mới**, chỉ khác là method PUT và có `{productId}` trong URL.

---

## 📂 Truy Cập Ảnh

Ảnh có thể truy cập qua URL:
```
http://localhost:8080/uploads/shops/{userId}/{filename}
http://localhost:8080/uploads/products/{userId}/{filename}
```

Ví dụ:
```
http://localhost:8080/uploads/shops/123/1234567890-uuid.jpg
```

---

## 💡 QUAN TRỌNG: Logic hoạt động

### 1️⃣ **Khi TẠO Shop/Product:**
```
User điền form + chọn ảnh từ máy
        ↓
POST /api/seller/shops (multipart/form-data)
        ↓
Controller upload ảnh → lưu vào uploads/
        ↓
Set logoUrl/imageUrls vào DTO
        ↓
Service lưu Shop/Product vào database (có URL ảnh)
        ↓
✅ XONG! Ảnh đã có trong database
```

### 2️⃣ **Khi KHÔNG có ảnh:**
- Không chọn file `logo`, `banner`, `images`
- Chỉ gửi `data` (JSON)
- Database lưu với `logoUrl = null`

### 3️⃣ **Khi UPDATE:**
- Nếu chọn ảnh mới → upload và thay URL cũ
- Nếu không chọn ảnh → giữ nguyên URL cũ

---

## 🧪 TEST VỚI CURL

### Tạo Shop với ảnh:
```bash
curl -X POST http://localhost:8080/api/seller/shops \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F 'data={"shopName":"Test Shop","phone":"0905123456","businessLicense":"GP001","taxCode":"TAX001","address":{"addressLine":"123 ABC","ward":"Ward 1","district":"District 1","city":"Da Nang","latitude":16.0544,"longitude":108.2022}}' \
  -F "logo=@D:/path/to/logo.jpg" \
  -F "banner=@D:/path/to/banner.jpg"
```

### Tạo Product với nhiều ảnh:
```bash
curl -X POST http://localhost:8080/api/seller/products \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F 'data={"shopId":1,"categoryId":1,"name":"Phở Bò","price":45000,"stockQuantity":100}' \
  -F "images=@D:/path/to/food1.jpg" \
  -F "images=@D:/path/to/food2.jpg" \
  -F "images=@D:/path/to/food3.jpg"
```

---

## 🎨 FRONTEND EXAMPLE (React + Axios)

```javascript
// Tạo Shop với logo và banner
const createShopWithImages = async (shopData, logoFile, bannerFile) => {
  const formData = new FormData();
  
  // Thêm JSON data dạng string
  formData.append('data', JSON.stringify(shopData));
  
  // Thêm files (nếu có)
  if (logoFile) formData.append('logo', logoFile);
  if (bannerFile) formData.append('banner', bannerFile);
  
  const response = await axios.post('/api/seller/shops', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'Authorization': `Bearer ${token}`
    }
  });
  
  return response.data;
};

// Tạo Product với nhiều ảnh
const createProductWithImages = async (productData, imageFiles) => {
  const formData = new FormData();
  
  // Thêm JSON data
  formData.append('data', JSON.stringify(productData));
  
  // Thêm nhiều ảnh
  imageFiles.forEach(file => {
    formData.append('images', file);
  });
  
  const response = await axios.post('/api/seller/products', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      'Authorization': `Bearer ${token}`
    }
  });
  
  return response.data;
};

// Component Example
function CreateShopForm() {
  const [shopData, setShopData] = useState({...});
  const [logoFile, setLogoFile] = useState(null);
  const [bannerFile, setBannerFile] = useState(null);
  
  const handleSubmit = async (e) => {
    e.preventDefault();
    try {
      const result = await createShopWithImages(shopData, logoFile, bannerFile);
      console.log('Shop created:', result);
      // Shop đã có logoUrl và bannerUrl trong database!
    } catch (error) {
      console.error('Error:', error);
    }
  };
  
  return (
    <form onSubmit={handleSubmit}>
      <input 
        type="text" 
        value={shopData.shopName}
        onChange={e => setShopData({...shopData, shopName: e.target.value})}
      />
      
      {/* Chọn logo - GIỐNG FACEBOOK */}
      <input 
        type="file" 
        accept="image/jpeg,image/jpg,image/png"
        onChange={e => setLogoFile(e.target.files[0])}
      />
      
      {/* Chọn banner */}
      <input 
        type="file" 
        accept="image/jpeg,image/jpg,image/png"
        onChange={e => setBannerFile(e.target.files[0])}
      />
      
      <button type="submit">Tạo Shop</button>
    </form>
  );
}
```

---

## ⚙️ YÊU CẦU KỸ THUẬT

- ✅ Định dạng: JPG, JPEG, PNG
- ✅ Kích thước: Max 5MB/file
- ✅ Tổng request: Max 20MB
- ✅ Validation tự động
- ✅ Tên file unique (timestamp + UUID)

---

## 🚨 ERROR HANDLING

| Error | Nguyên nhân |
|-------|-------------|
| "Thiếu dữ liệu shop" | Không gửi field `data` |
| "Chỉ chấp nhận JPG/PNG" | File không đúng định dạng |
| "Kích thước file quá 5MB" | File quá lớn |
| 401 Unauthorized | Chưa đăng nhập/token hết hạn |

---

**🎉 Xong! Logic giống y hệt Facebook - upload ảnh và lưu database trong 1 lần!**

