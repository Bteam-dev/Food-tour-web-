# Flow Tạo Shop với HERE API - Chi tiết

## 📍 Luồng hoạt động

### Bước 1: User nhập địa chỉ → Gọi Autocomplete
Frontend gọi API khi user đang gõ:

```javascript
// User gõ: "Quận Cầu Giấy"
GET /api/addresses/autocomplete?query=Quận Cầu Giấy&limit=5
```

**Response từ Backend:**
```json
[
  {
    "title": "Quận Cầu Giấy, Việt Nam",
    "id": "here:cm:namedplace:28310683",
    "addressLine": null,
    "ward": null,
    "district": "Quận Cầu Giấy",
    "city": "Hà Nội",
    "country": "Vietnam",
    "postalCode": "11312",
    "latitude": 21.02984,
    "longitude": 105.79953,
    "resultType": "locality"
  }
]
```

### Bước 2: User chọn địa chỉ → Frontend tự động điền form

Frontend nhận response và điền vào form tạo shop:

```javascript
// Khi user click chọn địa chỉ
function onSelectAddress(selectedAddress) {
  // Điền vào form
  form.addressLine = selectedAddress.addressLine;  // null
  form.ward = selectedAddress.ward;                // null
  form.district = selectedAddress.district;        // "Quận Cầu Giấy"
  form.city = selectedAddress.city;                // "Hà Nội"
  form.country = selectedAddress.country;          // "Vietnam"
  form.postalCode = selectedAddress.postalCode;    // "11312"
  form.latitude = selectedAddress.latitude;        // 21.02984
  form.longitude = selectedAddress.longitude;      // 105.79953
}
```

### Bước 3: User điền thông tin shop và bấm "Tạo"

Frontend gửi request:

```json
POST /api/shops
Authorization: Bearer {seller_token}
Content-Type: application/json

{
  "shopName": "Quán Ăn Hà Nội",
  "description": "Chuyên các món ăn Hà Nội",
  "phone": "0905123456",
  "address": {
    "addressLine": null,
    "ward": null,
    "district": "Quận Cầu Giấy",
    "city": "Hà Nội",
    "country": "Vietnam",
    "postalCode": "11312",
    "latitude": 21.02984,
    "longitude": 105.79953
  }
}
```

✅ **Backend nhận và tạo shop thành công!**

---

## 🎯 Mapping HERE API → DTO

### Input từ HERE API:
```json
{
  "address": {
    "houseNumber": "123",        // số nhà (optional)
    "street": "Nguyễn Văn Linh", // tên đường (optional)
    "district": "Hòa Xuân",      // phường/xã (optional)
    "city": "Quận Cầu Giấy",     // quận/huyện (khi có county)
    "county": "Hà Nội"           // tỉnh/thành phố
  }
}
```

### Output sang AddressSuggestionDTO:
```json
{
  "addressLine": "123 Nguyễn Văn Linh",  // houseNumber + street
  "ward": "Hòa Xuân",                    // district của HERE
  "district": "Quận Cầu Giấy",           // city của HERE (khi có county)
  "city": "Hà Nội",                      // county của HERE
  "country": "Vietnam",
  "latitude": 21.02984,
  "longitude": 105.79953
}
```

### Logic mapping:
```
if (county != null && !county.isEmpty()) {
  DTO.district = HERE.city;      // Quận Cầu Giấy
  DTO.city = HERE.county;        // Hà Nội
} else {
  DTO.district = null;
  DTO.city = HERE.city;          // Tỉnh/thành (khi không có county)
}
```

---

## 📝 Các trường hợp

### Case 1: Địa chỉ chi tiết (có số nhà + đường)
**User gõ:** "123 Nguyen Van Linh, Da Nang"

**HERE API trả về:**
```json
{
  "address": {
    "houseNumber": "123",
    "street": "Nguyễn Văn Linh",
    "district": "Hòa Xuân",
    "city": "Cẩm Lệ",
    "county": "Đà Nẵng"
  }
}
```

**DTO mapping:**
```json
{
  "addressLine": "123 Nguyễn Văn Linh",  ✅ CÓ
  "ward": "Hòa Xuân",                    ✅ CÓ
  "district": "Cẩm Lệ",                  ✅ CÓ
  "city": "Đà Nẵng",                     ✅ CÓ
  "latitude": 16.0544,
  "longitude": 108.2022
}
```

### Case 2: Chỉ chọn quận/huyện (locality)
**User gõ:** "Quận Cầu Giấy"

**HERE API trả về:**
```json
{
  "resultType": "locality",
  "address": {
    "city": "Quận Cầu Giấy",
    "county": "Hà Nội"
  }
}
```

**DTO mapping:**
```json
{
  "addressLine": null,                   ⚠️ NULL (không có số nhà/đường)
  "ward": null,                          ⚠️ NULL
  "district": "Quận Cầu Giấy",           ✅ CÓ
  "city": "Hà Nội",                      ✅ CÓ
  "latitude": 21.02984,
  "longitude": 105.79953
}
```

### Case 3: Chỉ chọn tỉnh/thành phố
**User gõ:** "Đà Nẵng"

**HERE API trả về:**
```json
{
  "resultType": "locality",
  "address": {
    "city": "Đà Nẵng"
  }
}
```

**DTO mapping:**
```json
{
  "addressLine": null,                   ⚠️ NULL
  "ward": null,                          ⚠️ NULL
  "district": null,                      ⚠️ NULL
  "city": "Đà Nẵng",                     ✅ CÓ (only city required)
  "latitude": 16.0544,
  "longitude": 108.2022
}
```

---

## 🖥️ Frontend Implementation Example

### HTML Form
```html
<form id="createShopForm">
  <input type="text" id="shopName" placeholder="Tên cửa hàng" required>
  <input type="text" id="phone" placeholder="Số điện thoại" required>
  
  <!-- Address autocomplete -->
  <input type="text" id="addressSearch" 
         placeholder="Nhập địa chỉ..." 
         autocomplete="off">
  <div id="suggestions"></div>
  
  <!-- Hidden fields - tự động điền -->
  <input type="hidden" id="addressLine">
  <input type="hidden" id="ward">
  <input type="hidden" id="district">
  <input type="hidden" id="city">
  <input type="hidden" id="latitude">
  <input type="hidden" id="longitude">
  
  <button type="submit">Tạo Shop</button>
</form>
```

### JavaScript
```javascript
let selectedAddress = null;

// 1. Autocomplete khi user gõ
document.getElementById('addressSearch').addEventListener('input', async (e) => {
  const query = e.target.value;
  if (query.length < 3) return;
  
  const response = await fetch(
    `/api/addresses/autocomplete?query=${encodeURIComponent(query)}&limit=5`
  );
  const suggestions = await response.json();
  
  // Hiển thị suggestions
  displaySuggestions(suggestions);
});

// 2. Hiển thị danh sách gợi ý
function displaySuggestions(suggestions) {
  const container = document.getElementById('suggestions');
  container.innerHTML = suggestions.map(s => `
    <div class="suggestion-item" data-address='${JSON.stringify(s)}'>
      ${s.title}
    </div>
  `).join('');
  
  // Click chọn
  document.querySelectorAll('.suggestion-item').forEach(item => {
    item.addEventListener('click', (e) => {
      const address = JSON.parse(e.target.dataset.address);
      selectAddress(address);
    });
  });
}

// 3. Khi user chọn địa chỉ
function selectAddress(address) {
  selectedAddress = address;
  
  // Điền vào form
  document.getElementById('addressSearch').value = address.title;
  document.getElementById('addressLine').value = address.addressLine || '';
  document.getElementById('ward').value = address.ward || '';
  document.getElementById('district').value = address.district || '';
  document.getElementById('city').value = address.city;
  document.getElementById('latitude').value = address.latitude;
  document.getElementById('longitude').value = address.longitude;
  
  // Ẩn suggestions
  document.getElementById('suggestions').innerHTML = '';
}

// 4. Submit form tạo shop
document.getElementById('createShopForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  
  const shopData = {
    shopName: document.getElementById('shopName').value,
    phone: document.getElementById('phone').value,
    address: {
      addressLine: selectedAddress.addressLine,
      ward: selectedAddress.ward,
      district: selectedAddress.district,
      city: selectedAddress.city,
      country: selectedAddress.country,
      postalCode: selectedAddress.postalCode,
      latitude: selectedAddress.latitude,
      longitude: selectedAddress.longitude
    }
  };
  
  const response = await fetch('/api/shops', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${localStorage.getItem('token')}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(shopData)
  });
  
  if (response.ok) {
    const shop = await response.json();
    alert('Tạo shop thành công! ID: ' + shop.id);
  }
});
```

---

## ✅ Validation

### Backend validation:
- ✅ `city` bắt buộc (`@NotBlank`)
- ⚠️ `addressLine` KHÔNG bắt buộc (vì có thể chọn locality)
- ⚠️ `ward`, `district` KHÔNG bắt buộc
- ✅ `shopName`, `phone` bắt buộc

### Database:
- ✅ `city` NOT NULL
- ⚠️ `address_line` cho phép NULL
- ⚠️ `ward`, `district` cho phép NULL

---

## 🎯 Kết luận

**Flow đầy đủ:**
1. User gõ → Gọi `/api/addresses/autocomplete`
2. Backend gọi HERE API → Map response → Trả về DTO
3. Frontend hiển thị gợi ý
4. User chọn → Frontend điền form
5. User submit → POST `/api/shops` với address đầy đủ
6. Backend tạo Address + Shop → Lưu DB

**Đã fix:**
- ✅ Mapping HERE API đúng với cấu trúc VN
- ✅ Cho phép `addressLine` null (khi chọn locality)
- ✅ Frontend có đủ thông tin để điền form
- ✅ Backend validate đúng

