# HƯỚNG DẪN TEST CHATBOT SAU KHI FIX

## BƯỚC 1: RESYNC VECTOR INDEX (LÀM MỚI DỮ LIỆU)

Trước khi test chatbot, BẮT BUỘC phải resync lại vector index để xóa data cũ và index lại với logic mới.

### Cách 1: Qua Postman (Khuyến nghị)

**Request:**
```
POST http://localhost:8080/api/admin/vector/resync
```

**Headers:**
```
Authorization: Bearer <ADMIN_TOKEN>
Content-Type: application/json
```

**Response thành công:**
```json
{
    "success": true,
    "message": "Started full vector index resync. Check server logs for progress."
}
```

**Kiểm tra logs server** để xem quá trình sync:
```
2026-03-19 20:55:00 INFO  - Admin triggered full vector index resync
2026-03-19 20:55:02 INFO  - Synced 6 available products to vector store.
```

### Cách 2: Xóa ElasticSearch data folder và restart app

```bash
# Trên Windows
rd /s /q D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\ElasticSearch_data

# Sau đó restart Spring Boot app
# App sẽ tự động sync lại lần đầu
```

---

## BƯỚC 2: LẤY ADMIN TOKEN (Nếu chưa có)

### 2.1. Login với tài khoản ADMIN

**Request:**
```
POST http://localhost:8080/api/public/auth/login
```

**Body:**
```json
{
    "username": "admin",
    "password": "admin123"
}
```

**Response:**
```json
{
    "data": {
        "token": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOi...",
        "user": {
            "id": 1,
            "username": "admin",
            "role": "ADMIN"
        }
    },
    "success": true
}
```

**Lưu lại `token`** để dùng cho bước 1.

---

## BƯỚC 3: TẠO CHATBOT CONVERSATION (Nếu chưa có)

### 3.1. Login với tài khoản USER

**Request:**
```
POST http://localhost:8080/api/public/auth/login
```

**Body:**
```json
{
    "username": "testuser",
    "password": "password123"
}
```

Hoặc đăng ký user mới nếu chưa có.

### 3.2. Tạo conversation

**Request:**
```
POST http://localhost:8080/api/user/chatbot/conversations
```

**Headers:**
```
Authorization: Bearer <USER_TOKEN>
Content-Type: application/json
```

**Body:**
```json
{
    "title": "Test Chatbot"
}
```

**Response:**
```json
{
    "data": {
        "id": 1,
        "title": "Test Chatbot",
        "createdAt": "2026-03-19T20:00:00"
    },
    "success": true
}
```

**Lưu lại `conversationId` (id = 1)** để dùng cho bước tiếp theo.

---

## BƯỚC 4: TEST CHATBOT VỚI CÁC CASE KHÁC NHAU

Sau khi đã resync xong, bây giờ test chatbot:

### Test Case 1: Tìm quán bán món cụ thể

**Request:**
```
POST http://localhost:8080/api/user/chatbot/1/messages
```

**Headers:**
```
Authorization: Bearer <USER_TOKEN>
Content-Type: application/json
```

**Body:**
```json
{
    "content": "App có quán nào bán gỏi cuốn không"
}
```

**Expected Response:**
```json
{
    "data": {
        "id": 2,
        "senderType": "BOT",
        "senderName": "FoodTour Bot",
        "content": "Có bro! Quán MinhFood có bán Gỏi cuốn tôm thịt, giá 20k. Món này tươi, ít dầu mỡ, phù hợp người ăn healthy nha!",
        "createdAt": "2026-03-19T20:10:00",
        "navigationUrls": [
            "product_detail/7"
        ]
    },
    "success": true,
    "message": "Message sent and replied successfully"
}
```

**Kiểm tra:**
- ✅ Bot trả lời ĐÚNG (không còn echo lại câu hỏi)
- ✅ Bot mention tên quán "MinhFood"
- ✅ Bot đề cập tên món "Gỏi cuốn tôm thịt"
- ✅ Có `navigationUrls` với product_id = 7

---

### Test Case 2: Tìm món theo tình huống (trời nóng)

**Body:**
```json
{
    "content": "Trời đang nóng, có món nào giải nhiệt không"
}
```

**Expected Response:**
```json
{
    "data": {
        "senderType": "BOT",
        "content": "Trời nóng thì nên ăn món mát bro! App có Rau má đậu xanh ở quán MinhFood cơ sở 2, giá 10k (giảm còn 8k), giúp thanh lọc cơ thể. Hoặc Gỏi cuốn tôm thịt ở MinhFood, 20k, ít dầu mỡ, rất healthy đó!",
        "navigationUrls": [
            "product_detail/3",
            "product_detail/7"
        ]
    }
}
```

**Kiểm tra:**
- ✅ Bot SUY LUẬN đúng (trời nóng → món mát/giải nhiệt)
- ✅ Bot gợi ý Rau má đậu xanh (món giải nhiệt)
- ✅ Bot gợi ý Gỏi cuốn (món healthy, nhẹ)
- ✅ Có nhiều navigationUrls

---

### Test Case 3: Tìm món theo giá

**Body:**
```json
{
    "content": "Có món nào giá rẻ dưới 20k không"
}
```

**Expected Response:**
```json
{
    "data": {
        "content": "Có bro! Bánh mì pate 15k và Bột chiên 15k đều giá rẻ đó. Bánh mì ăn sáng hoặc ăn nhanh rất tiện. Bột chiên giòn rụm, ăn vặt cũng ngon!",
        "navigationUrls": [
            "product_detail/5",
            "product_detail/6"
        ]
    }
}
```

**Kiểm tra:**
- ✅ Bot lọc theo giá đúng (< 20k)
- ✅ Gợi ý Bánh mì pate (15k) và Bột chiên (15k)

---

### Test Case 4: Tìm món chuẩn bị nhanh

**Body:**
```json
{
    "content": "Món nào chuẩn bị nhanh nhất"
}
```

**Expected Response:**
```json
{
    "data": {
        "content": "Gỏi cuốn tôm thịt và Bánh mì pate chuẩn bị chỉ 5 phút thôi bro! Gỏi cuốn 20k ở MinhFood, bánh mì 15k, cả 2 đều ăn nhanh tiện lợi đó!",
        "navigationUrls": [
            "product_detail/7",
            "product_detail/5"
        ]
    }
}
```

**Kiểm tra:**
- ✅ Bot nhìn vào `preparationTime` (5 phút)
- ✅ Gợi ý đúng món nhanh nhất

---

### Test Case 5: Tìm món theo rating cao

**Body:**
```json
{
    "content": "Món nào được đánh giá cao nhất"
}
```

**Hiện tại data có rating = 0**, nên bot sẽ trả lời:
```json
{
    "content": "Hiện tại các món trong app chưa có đánh giá bro. Hãy thử các món mới và để lại review nha!"
}
```

**Sau khi có review**, bot sẽ gợi ý món có rating cao nhất.

---

### Test Case 6: Trời lạnh ăn gì

**Body:**
```json
{
    "content": "Trời lạnh ăn gì cho ấm"
}
```

**Expected Response:**
```json
{
    "data": {
        "content": "Trời lạnh thì làm bát phở hoặc bún bò nóng là hợp lý đó bro. App có Bún bò Huế 30k ở MinhFood, nước dùng đậm vị cay nhẹ, ăn ấm lắm. Hoặc Phở bò tái 15k cũng nóng hổi ngon!",
        "navigationUrls": [
            "product_detail/8",
            "product_detail/4"
        ]
    }
}
```

**Kiểm tra:**
- ✅ Bot SUY LUẬN đúng (trời lạnh → món nóng, cay)
- ✅ Gợi ý Bún bò Huế (có "cay nhẹ" trong description)
- ✅ Gợi ý Phở bò tái (món nóng)

---

### Test Case 7: Ăn healthy/nhẹ

**Body:**
```json
{
    "content": "Tôi muốn ăn healthy, có món gì không"
}
```

**Expected Response:**
```json
{
    "data": {
        "content": "Gỏi cuốn tôm thịt là lựa chọn healthy bro! Món này ít dầu mỡ, nhiều rau sống, 200 calories thôi. Hoặc Rau má đậu xanh giúp thanh lọc cơ thể cũng rất tốt!",
        "navigationUrls": [
            "product_detail/7",
            "product_detail/3"
        ]
    }
}
```

**Kiểm tra:**
- ✅ Bot nhìn vào `nutritionInfo` (calories thấp, fat thấp)
- ✅ Bot nhìn vào `description` (có "healthy", "ít dầu mỡ")
- ✅ Gợi ý Gỏi cuốn (200 cal) và Rau má

---

### Test Case 8: Câu hỏi không liên quan

**Body:**
```json
{
    "content": "Hôm nay thời tiết thế nào"
}
```

**Expected Response:**
```json
{
    "data": {
        "content": "Sorry bro, tao chỉ giúp về ăn uống trong app thôi!",
        "navigationUrls": []
    }
}
```

**Kiểm tra:**
- ✅ Bot từ chối trả lời câu hỏi off-topic
- ✅ Không có navigationUrls

---

## BƯỚC 5: KIỂM TRA NAVIGATION URLS TRONG ANDROID

Sau khi nhận được response từ backend, Android app có thể sử dụng `navigationUrls`:

```kotlin
// Kotlin code trong Android app
val response = chatbotApi.sendMessage(conversationId, content)

// Lấy navigation URLs
val navUrls = response.data.navigationUrls // ["product_detail/7", "product_detail/8"]

// Navigate đến product detail
navUrls.forEach { url ->
    // url = "product_detail/7"
    navController.navigate(url) // Jetpack Compose Navigation sẽ tự parse
}

// Hoặc tạo clickable links trong chat UI
Text(
    text = "Xem món này",
    modifier = Modifier.clickable {
        navController.navigate(navUrls[0])
    }
)
```

---

## GIẢI QUYẾT VẤN ĐỀ INDEX CŨ

### Vấn đề gặp phải trước đây:
- Elasticsearch vẫn chứa data từ index cũ
- Products bị soft-deleted (isAvailable = false) vẫn được index
- Prompt template cũ không hướng dẫn AI suy luận

### Giải pháp Production:

#### 1. **Incremental Update** (Event-Driven Sync)
File: `ProductListenerConfig.java`

```java
@PostPersist
@PostUpdate
public void onSave(Product product) {
    // Khi product được tạo/update → tự động sync
    vectorService.syncProduct(product);
}

@PreRemove
public void onDelete(Product product) {
    // Khi product bị xóa → tự động xóa khỏi ES
    vectorService.deleteProduct(product.getId());
}
```

**Ưu điểm:**
- Không cần resync toàn bộ khi có thay đổi nhỏ
- Tự động cập nhật ES khi CRUD product
- Giảm tải server

#### 2. **Filter Soft-Deleted Products**
File: `FoodVectorServiceImpl.java` line 158-161

```java
// CHỈ sync products có isAvailable = true
List<Product> products = productRepository.findAll().stream()
    .filter(p -> p.getIsAvailable() != null && p.getIsAvailable())
    .toList();
```

**Và trong `syncProduct()`** (line 208-213):
```java
// Nếu product bị soft-delete, XÓA khỏi ES
if (product.getIsAvailable() == null || !product.getIsAvailable()) {
    deleteProduct(product.getId());
    return;
}
```

#### 3. **Chỉ Sync Lần Đầu Hoặc Khi Admin Trigger**
File: `FoodVectorServiceImpl.java` line 111-123

```java
private void checkAndSyncIfNeeded() {
    long count = esClient.count(c -> c.index(esIndex)).count();
    if (count == 0) {
        // ES trống → sync lần đầu
        syncAllProducts();
    } else {
        // ES đã có data → skip, dùng event-driven sync
        log.info("ES already has {} products → skip sync", count);
    }
}
```

**Khi nào cần force resync?**
- Thay đổi embedding logic
- Thay đổi `buildProductText()` (thêm field mới)
- Fix lỗi data cũ trong index
- Sau khi bulk update/delete products ngoài app

---

## BEST PRACTICES TRONG PRODUCTION

### 1. Monitoring
Thêm metrics để theo dõi:
- Số lượng documents trong ES (`GET /_cat/indices`)
- Thời gian sync
- Số lượng queries/giây

### 2. Scheduled Cleanup
Tạo job chạy hàng đêm để cleanup orphan documents:
```java
@Scheduled(cron = "0 0 2 * * ?") // 2 AM mỗi ngày
public void cleanupOrphanDocuments() {
    // Lấy tất cả product_id từ ES
    // So sánh với DB
    // Xóa documents không tồn tại trong DB
}
```

### 3. Versioning
Thêm version vào ES document để track changes:
```java
doc.put("version", product.getUpdatedAt().toString());
```

### 4. Batch Operations
Khi sync nhiều products, dùng Bulk API (đã implement):
```java
esClient.bulk(BulkRequest.of(b -> b.operations(ops)));
```

---

## TROUBLESHOOTING

### Vấn đề: Bot vẫn trả lời ngu sau khi resync
**Nguyên nhân:** ElasticSearch cache hoặc Caffeine cache
**Giải pháp:**
```bash
# Restart app
# Hoặc clear cache
POST http://localhost:8080/api/admin/cache/clear
```

### Vấn đề: Navigation URLs trống
**Nguyên nhân:** Query không match với products (score < minScore)
**Giải pháp:** Giảm `rag.min-score` trong `application.properties`:
```properties
rag.min-score=0.5  # từ 0.7 xuống 0.5
```

### Vấn đề: Bot không suy luận được
**Nguyên nhân:** Prompt template chưa đủ chi tiết hoặc CONTEXT rỗng
**Giải pháp:** Kiểm tra logs xem CONTEXT có được retrieve không:
```
DEBUG log in ChatbotAI: print retrieved content
```

---

## KẾT LUẬN

Sau khi fix:
1. ✅ Chatbot SUY LUẬN thông minh theo tình huống
2. ✅ Index chỉ chứa available products
3. ✅ Incremental update tự động khi CRUD
4. ✅ Navigation URLs để điều hướng trong Android
5. ✅ Production-ready sync strategy

**Lưu ý:** Nhớ RESYNC lần đầu sau khi deploy code mới!
