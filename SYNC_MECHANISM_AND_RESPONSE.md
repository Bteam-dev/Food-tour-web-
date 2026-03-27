# GIẢI THÍCH CƠ CHẾ SYNC VÀ RESPONSE CẢI TIẾN

## 📌 1. CƠ CHẾ SYNC - TRẢ LỜI CÂU HỎI CỦA BẠN

### ❓ "Resync là sync lại từ đầu à?"

**Đúng rồi!** Có 2 loại sync:

#### A. **FULL RESYNC** (Sync lại TẤT CẢ từ đầu)

**Khi nào xảy ra?**
1. ❌ **KHÔNG tự động** khi database thay đổi
2. ✅ **CHỈ khi Admin gọi API:** `POST /api/admin/vector/resync`
3. ✅ Lần đầu tiên chạy app (khi ES trống)

**Code:**
```java
// syncAllProducts() - Line 151
public void syncAllProducts() {
    // Lấy TẤT CẢ products từ DB
    List<Product> products = productRepository.findAll();
    
    // Index lại TẤT CẢ vào Elasticsearch
    // Overwrite hết data cũ
}
```

**Đặc điểm:**
- ✅ Sync lại **TẤT CẢ** products
- ✅ Overwrite toàn bộ index cũ
- ⚠️ Tốn tài nguyên, chạy lâu nếu nhiều data

---

#### B. **INCREMENTAL SYNC** (Chỉ sync những gì thay đổi)

**Khi nào xảy ra?**
1. ✅ **TỰ ĐỘNG** khi TẠO product mới
2. ✅ **TỰ ĐỘNG** khi UPDATE product
3. ✅ **TỰ ĐỘNG** khi XÓA product
4. ✅ **TỰ ĐỘNG** khi soft-delete (isAvailable = false)

**Code:**
```java
// ProductEsSyncListener.java
@PostPersist  // Khi INSERT vào DB
@PostUpdate   // Khi UPDATE trong DB
public void onSave(Product product) {
    vectorService.syncProduct(product); // CHỈ sync 1 product này
}

@PreRemove    // Khi DELETE khỏi DB
public void onDelete(Product product) {
    vectorService.deleteProduct(product.getId()); // CHỈ xóa 1 product này
}
```

**Đặc điểm:**
- ✅ **CHỈ sync 1 product** bị thay đổi
- ✅ **KHÔNG sync lại từ đầu**
- ✅ Real-time, nhanh chóng
- ✅ Tiết kiệm tài nguyên

---

### 🎯 SO SÁNH 2 LOẠI SYNC

| | Full Resync | Incremental Sync |
|---|-------------|------------------|
| **Trigger** | Admin gọi API thủ công | Tự động khi CRUD |
| **Phạm vi** | TẤT CẢ products | CHỈ 1 product |
| **Thời gian** | Lâu (10s với 1000 products) | Nhanh (<100ms) |
| **Tài nguyên** | Cao | Thấp |
| **Khi nào dùng** | Sửa code logic, fix bug | Thao tác thường ngày |

---

### 💡 VÍ DỤ THỰC TẾ

#### **Scenario 1: User tạo món mới "Cơm tấm"**

```
1. POST /api/seller/shop/1/products
   Body: { "name": "Cơm tấm", "price": 30000 }

2. productRepository.save(comTam)
   → Trigger @PostPersist

3. syncProduct(comTam)
   → CHỈ index "Cơm tấm" vào ES (1 product thôi)
   → KHÔNG sync lại 999 products còn lại

4. Chatbot ngay lập tức biết món "Cơm tấm"
```

✅ **Incremental Sync** - Chỉ 1 món, tốn < 100ms

---

#### **Scenario 2: Seller giảm giá "Phở bò" từ 50k → 30k**

```
1. PUT /api/seller/shop/1/products/4
   Body: { "price": 30000 }

2. productRepository.save(pho)
   → Trigger @PostUpdate

3. syncProduct(pho)
   → CHỈ update "Phở bò" trong ES (1 product)
   → KHÔNG sync lại products khác

4. Chatbot trả lời giá mới 30k
```

✅ **Incremental Sync** - Chỉ 1 món

---

#### **Scenario 3: Admin sửa prompt template (code logic thay đổi)**

```
1. Dev sửa ChatbotAI.java (thêm logic suy luận mới)
2. Build & deploy code mới

3. Admin gọi: POST /api/admin/vector/resync

4. syncAllProducts()
   → Index lại TẤT CẢ 1000 products
   → Overwrite toàn bộ ES
   → Tốn 10-20 giây

5. Chatbot dùng logic mới cho TẤT CẢ products
```

✅ **Full Resync** - Vì logic thay đổi, cần áp dụng cho tất cả

---

### 🔧 KHI NÀO CẦN FULL RESYNC?

**BẮT BUỘC phải resync khi:**
1. ✅ Sửa `buildProductText()` (thêm field mới vào text)
2. ✅ Sửa prompt template (thay đổi logic AI)
3. ✅ Đổi embedding model (ví dụ: nomic-embed-text → other-model)
4. ✅ Fix lỗi data cũ trong index
5. ✅ Sau khi bulk update/delete nhiều products ngoài app

**KHÔNG cần resync khi:**
1. ❌ Tạo/update/xóa 1 product thông thường
2. ❌ User thao tác CRUD bình thường
3. ❌ Restart app (ES data đã có sẵn trên disk)

---

## 🆕 2. RESPONSE MỚI - DANH SÁCH KẾT QUẢ KÈM ĐƯỜNG DẪN

Tôi đã thêm **`suggestedProducts`** vào response!

### Response Cũ (trước đây):
```json
{
    "content": "Có bro! Quán MinhFood có bán Gỏi cuốn...",
    "navigationUrls": ["product_detail/7"]
}
```

### Response Mới (bây giờ):
```json
{
    "id": 2,
    "senderType": "BOT",
    "senderName": "FoodTour Bot",
    "content": "Có bro! Quán MinhFood có bán Gỏi cuốn tôm thịt, giá 20k. Món này tươi, ít dầu mỡ, phù hợp người ăn healthy nha!",
    "createdAt": "2026-03-19T21:00:00",
    
    "navigationUrls": [
        "product_detail/7",
        "product_detail/8"
    ],
    
    "suggestedProducts": [
        {
            "productId": 7,
            "productName": "Gỏi cuốn tôm thịt",
            "shopName": "MinhFood",
            "shopId": 1,
            "price": 20000.00,
            "discountPrice": null,
            "rating": 4.5,
            "totalReviews": 120,
            "imageUrl": "/uploads/ProductImage/user_2/shop_1/product_7/1773854565920-e9b835d6.jpg",
            "navigationUrl": "product_detail/7"
        },
        {
            "productId": 8,
            "productName": "Bún bò Huế",
            "shopName": "MinhFood",
            "shopId": 1,
            "price": 30000.00,
            "discountPrice": 25000.00,
            "rating": 4.8,
            "totalReviews": 85,
            "imageUrl": "/uploads/ProductImage/user_2/shop_1/product_8/1773857409715-e6335431.jpg",
            "navigationUrl": "product_detail/8"
        }
    ]
}
```

### Ưu điểm:

#### 1. **Android app có thể hiển thị list view đẹp**
```kotlin
// Kotlin - Android app
LazyColumn {
    items(response.suggestedProducts) { product ->
        ProductCard(
            imageUrl = product.imageUrl,
            name = product.productName,
            shopName = product.shopName,
            price = product.price,
            discountPrice = product.discountPrice,
            rating = product.rating,
            onClick = {
                navController.navigate(product.navigationUrl)
            }
        )
    }
}
```

#### 2. **Không cần gọi thêm API để lấy thông tin món**
- **Trước:** Bot trả về product IDs → App phải gọi API `/products/{id}` cho từng món
- **Bây giờ:** Bot trả về đầy đủ thông tin luôn → App hiển thị ngay

#### 3. **Hiển thị thumbnail, giá, rating ngay trong chat**
- User thấy ảnh món ăn
- User thấy giá, giá giảm (nếu có)
- User thấy rating trước khi click vào

---

## 📱 CÁCH ANDROID APP SỬ DỤNG

### UI Example (Jetpack Compose):

```kotlin
@Composable
fun ChatMessageWithSuggestions(message: ChatbotMessageResponse) {
    Column {
        // Text response từ bot
        Text(
            text = message.content,
            style = MaterialTheme.typography.bodyMedium
        )
        
        // Danh sách sản phẩm gợi ý
        if (message.suggestedProducts.isNotEmpty()) {
            Text(
                text = "Gợi ý cho bạn:",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(message.suggestedProducts) { product ->
                    ProductSuggestionCard(
                        product = product,
                        onClick = {
                            navController.navigate(product.navigationUrl)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ProductSuggestionCard(
    product: ProductSuggestion,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .clickable { onClick() }
    ) {
        Column {
            // Thumbnail
            AsyncImage(
                model = "http://localhost:8080${product.imageUrl}",
                contentDescription = product.productName,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                contentScale = ContentScale.Crop
            )
            
            // Tên món
            Text(
                text = product.productName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Quán
            Text(
                text = product.shopName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
            
            // Giá
            Row {
                if (product.discountPrice != null) {
                    Text(
                        text = "${product.discountPrice.toInt()}đ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Red
                    )
                    Text(
                        text = "${product.price.toInt()}đ",
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        text = "${product.price.toInt()}đ",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            
            // Rating
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "${product.rating} (${product.totalReviews})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
```

### Kết quả UI:

```
┌───────────────────────────────────┐
│ BOT: Có bro! Quán MinhFood có...│
├───────────────────────────────────┤
│ Gợi ý cho bạn:                    │
│                                   │
│ ┌──────┐  ┌──────┐  ┌──────┐    │
│ │ [🖼️] │  │ [🖼️] │  │ [🖼️] │    │
│ │Gỏi   │  │Bún bò│  │Phở bò│    │
│ │cuốn │  │Huế   │  │tái   │    │
│ │20k   │  │30k   │  │15k   │    │
│ │⭐4.5 │  │⭐4.8 │  │⭐4.2 │    │
│ └──────┘  └──────┘  └──────┘    │
└───────────────────────────────────┘
```

---

## 🎯 TÓM TẮT

### 1. Về Sync:
- ✅ **CRUD thường ngày** → Incremental Sync (tự động, chỉ 1 product)
- ✅ **Sửa code logic** → Full Resync (thủ công, tất cả products)
- ✅ **KHÔNG tự động resync** khi database thay đổi
- ✅ **Event-driven** cho mọi thao tác CRUD

### 2. Về Response:
- ✅ **Text response** (content) - Câu trả lời của bot
- ✅ **Navigation URLs** - Danh sách đường dẫn
- ✅ **Suggested Products** - Danh sách sản phẩm đầy đủ thông tin (NEW!)
  - Tên món, quán, giá, rating, ảnh
  - Sẵn sàng hiển thị trong Android list view

### 3. Lợi ích:
- ⚡ **Performance:** Incremental sync nhanh, tiết kiệm tài nguyên
- 🎨 **UX:** Android app hiển thị thumbnail, giá, rating ngay trong chat
- 🚀 **Developer-friendly:** Không cần gọi thêm API lấy thông tin món

---

**Build thành công!** ✅ Code sẵn sàng test với Postman!
