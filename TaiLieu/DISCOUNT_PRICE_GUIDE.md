# HƯỚNG DẪN: LOGIC GIÁ GIẢM (DISCOUNT PRICE) THỰC TẾ

## 📊 Vấn đề đã fix

Trước đây `discountPrice` trong Product **đang để cho có**, không có logic tính toán thật sự. Giờ đã sửa xong toàn bộ flow để **giống logic thực tế**:

---

## ✅ Những gì đã làm:

### 1. **Entity Product - Thêm helper methods**
```java
// Lấy giá hiệu lực (ưu tiên discountPrice nếu có)
public BigDecimal getEffectivePrice() {
    return (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0) 
            ? discountPrice 
            : price;
}

// Kiểm tra xem có đang giảm giá không
public boolean hasDiscount() {
    return discountPrice != null 
            && discountPrice.compareTo(BigDecimal.ZERO) > 0 
            && discountPrice.compareTo(price) < 0;
}

// Tính % giảm giá
public BigDecimal getDiscountPercentage() {
    if (!hasDiscount()) {
        return BigDecimal.ZERO;
    }
    return price.subtract(discountPrice)
            .divide(price, 4, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(2, RoundingMode.HALF_UP);
}
```

### 2. **CartServiceImpl - Tính theo giá hiệu lực**
```java
// TRƯỚC (SAI):
BigDecimal totalPrice = product.getPrice().multiply(...);

// SAU (ĐÚNG):
BigDecimal effectivePrice = product.getEffectivePrice();
BigDecimal totalPrice = effectivePrice.multiply(...);
```

### 3. **OrderServiceImpl - Tính subtotal và orderItem theo giá hiệu lực**
```java
// Tính subtotal
BigDecimal effectivePrice = cartItem.getProduct().getEffectivePrice();
BigDecimal itemTotal = effectivePrice.multiply(BigDecimal.valueOf(quantity));
subtotal = subtotal.add(itemTotal);

// Lưu vào OrderItem
orderItem.setUnitPrice(effectivePrice); // Lưu giá thực tế đã bán
orderItem.setTotalPrice(itemTotal);
```

### 4. **WalletService - Commission tính đúng**
Commission (hoa hồng) tính dựa trên `order.getTotalAmount()`, mà `totalAmount` đã được tính từ `effectivePrice` → **Commission tự động đúng!**

```java
// Hoa hồng = 12% * totalAmount (đã tính theo giá giảm nếu có)
BigDecimal commissionAmount = order.getTotalAmount()
    .multiply(commissionRate)
    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
```

### 5. **ProductServiceImpl - Validation khi create/update**
```java
// VALIDATION khi tạo/sửa sản phẩm:
if (discountPrice != null) {
    if (discountPrice >= price) {
        throw new RuntimeException("Giá giảm phải nhỏ hơn giá gốc");
    }
    if (discountPrice < 0) {
        throw new RuntimeException("Giá giảm không thể âm");
    }
}
```

---

## 🔄 Flow hoạt động

### **Kịch bản 1: Sản phẩm KHÔNG có giảm giá**
```json
// Product
{
  "price": 50000,
  "discountPrice": null
}

// → getEffectivePrice() = 50000 (price gốc)
```

**Khi thêm vào cart:**
- Cart tính: 50000 × quantity
- Order tính: 50000 × quantity
- Commission tính: 12% × (50000 × quantity)

### **Kịch bản 2: Sản phẩm CÓ giảm giá**
```json
// Product
{
  "price": 50000,
  "discountPrice": 35000  // Giảm 30%
}

// → getEffectivePrice() = 35000 (giá giảm)
// → getDiscountPercentage() = 30.00%
// → hasDiscount() = true
```

**Khi thêm vào cart:**
- Cart tính: **35000** × quantity (theo giá giảm)
- Order tính: **35000** × quantity
- Commission tính: 12% × (**35000** × quantity)

**Ví dụ cụ thể:**
- Khách mua 2 sản phẩm
- Subtotal = 35000 × 2 = **70,000 VND**
- DeliveryFee = 15,000 VND
- Tax (10%) = 7,000 VND
- **Total = 92,000 VND**
- Commission (12%) = 11,040 VND
- Seller nhận = **80,960 VND**

---

## 📋 API Response mẫu

### **GET /api/public/products/{id}**
```json
{
  "id": 1,
  "name": "Phở Bò Tái",
  "price": 50000,
  "discountPrice": 35000,    // ← Có giảm giá
  "description": "Phở bò truyền thống",
  "isAvailable": true,
  ...
}
```

### **GET /api/user/cart**
```json
{
  "items": [
    {
      "productId": 1,
      "productName": "Phở Bò Tái",
      "unitPrice": 50000,           // Giá gốc (để hiển thị)
      "quantity": 2,
      "totalPrice": 70000,          // ← Tính theo giá giảm (35000 × 2)
      "selectedVariants": []
    }
  ]
}
```

### **POST /api/user/orders**
```json
{
  "orderId": 123,
  "subtotal": 70000,               // ← Tính theo giá giảm
  "deliveryFee": 15000,
  "taxAmount": 7000,
  "totalAmount": 92000,
  "platformCommissionRate": 12.00,
  "platformCommissionAmount": 11040,  // ← 12% × 92000
  "sellerReceivedAmount": 80960,     // ← 92000 - 11040
  "orderItems": [
    {
      "productId": 1,
      "productName": "Phở Bò Tái",
      "unitPrice": 35000,            // ← Lưu giá THỰC TẾ đã bán
      "quantity": 2,
      "totalPrice": 70000
    }
  ]
}
```

---

## 🧪 Test Cases

### **Test 1: Tạo sản phẩm với giá giảm hợp lệ**
```bash
POST /api/seller/products
{
  "name": "Bún Bò Huế",
  "price": 45000,
  "discountPrice": 35000,  # Giảm 22%
  "shopId": 1,
  "categoryId": 2
}

# ✅ SUCCESS: Product created
```

### **Test 2: Tạo sản phẩm với giá giảm SAI (>= price gốc)**
```bash
POST /api/seller/products
{
  "name": "Bún Bò Huế",
  "price": 45000,
  "discountPrice": 50000,  # SAI: Giá giảm > giá gốc
  "shopId": 1,
  "categoryId": 2
}

# ❌ ERROR: "Giá giảm phải nhỏ hơn giá gốc"
```

### **Test 3: Tạo sản phẩm với giá giảm âm**
```bash
POST /api/seller/products
{
  "name": "Bún Bò Huế",
  "price": 45000,
  "discountPrice": -5000,  # SAI: Giá âm
  "shopId": 1,
  "categoryId": 2
}

# ❌ ERROR: "Giá giảm không thể âm"
```

### **Test 4: Thêm vào cart - tính theo giá giảm**
```bash
# Sản phẩm có: price = 50000, discountPrice = 35000

POST /api/user/cart
{
  "productId": 1,
  "quantity": 3
}

GET /api/user/cart
# → totalPrice = 35000 × 3 = 105,000 VND ✅
```

### **Test 5: Tạo đơn hàng - commission tính đúng**
```bash
# Cart có: 1 sản phẩm (discountPrice = 35000) × 2 = 70,000

POST /api/user/orders
{
  "cartItemIds": [1],
  "paymentMethod": "WALLET"
}

# → subtotal = 70,000
# → totalAmount = 70,000 + 15,000 + 7,000 = 92,000
# → commission = 92,000 × 12% = 11,040 ✅
# → seller receives = 80,960 ✅
```

---

## 🎯 Lợi ích

### ✅ **Cho Seller:**
- Tự do set giá giảm để thu hút khách
- Commission tính dựa trên giá BÁN (không phải giá gốc) → công bằng
- Dễ dàng tạo flash sale, khuyến mãi

### ✅ **Cho Platform:**
- Commission luôn đúng với giá thực tế
- Không bị lỗi tính toán
- Revenue statistics chính xác

### ✅ **Cho User:**
- Thấy rõ được giảm giá bao nhiêu %
- Giá hiển thị = giá thanh toán (minh bạch)
- Không bị charge giá gốc khi có discount

---

## 📊 Database Schema

### **products table**
```sql
CREATE TABLE products (
  id INT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(150) NOT NULL,
  price DECIMAL(15,2) NOT NULL,              -- Giá gốc
  discount_price DECIMAL(15,2) NULL,         -- Giá giảm (nullable)
  ...
);
```

**Ví dụ data:**
```sql
-- Sản phẩm KHÔNG giảm giá
INSERT INTO products (name, price, discount_price) 
VALUES ('Phở Gà', 40000, NULL);

-- Sản phẩm CÓ giảm giá
INSERT INTO products (name, price, discount_price) 
VALUES ('Phở Bò', 50000, 35000);
```

---

## 🔍 Validation Rules

| Rule | Mô tả | Error Message |
|------|-------|---------------|
| `discountPrice < price` | Giá giảm phải nhỏ hơn giá gốc | "Giá giảm phải nhỏ hơn giá gốc" |
| `discountPrice >= 0` | Giá giảm không được âm | "Giá giảm không thể âm" |
| `discountPrice` nullable | Có thể NULL (không giảm giá) | - |

---

## 📌 Files đã thay đổi

```
✅ entity/Product.java                        → + getEffectivePrice(), hasDiscount(), getDiscountPercentage()
✅ service/impl/CartServiceImpl.java          → Tính theo effectivePrice
✅ service/impl/OrderServiceImpl.java         → Tính subtotal và orderItem.unitPrice theo effectivePrice
✅ service/impl/ProductServiceImpl.java       → + Validation discountPrice
✅ service/impl/WalletServiceImpl.java        → Commission tự động đúng (dựa trên totalAmount)
📄 TaiLieu/DISCOUNT_PRICE_GUIDE.md           → File hướng dẫn này
```

---

## 🎉 Kết luận

**Giờ logic giá giảm đã THỰC SỰ hoạt động đúng:**
- ✅ Cart tính theo giá giảm
- ✅ Order tính theo giá giảm
- ✅ Commission tính theo giá thực tế bán
- ✅ Revenue/Statistics chính xác
- ✅ Validation đầy đủ
- ✅ FE chỉ cần hiển thị, BE lo hết!

**Seller có thể:**
- Set discountPrice = null → bán giá gốc
- Set discountPrice = 35000 (khi price = 50000) → giảm giá 30%
- Update bất cứ lúc nào để tạo flash sale

**BE tự động tính toán tất cả, không cần FE làm gì thêm!** 🚀

