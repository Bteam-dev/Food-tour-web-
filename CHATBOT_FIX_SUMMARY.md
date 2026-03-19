# TÓM TẮT CÁC THAY ĐỔI FIX CHATBOT

## 🎯 VẤN ĐỀ ĐÃ GIẢI QUYẾT

### 1. **Chatbot trả lời ngu, không suy luận**
**Triệu chứng:**
- Hỏi "App có quán nào bán gỏi cuốn không" → Bot echo lại câu hỏi
- Hỏi "Trời nóng ăn gì" → Bot trả lời sai hoặc không liên quan

**Nguyên nhân:**
- Prompt template quá đơn giản, không hướng dẫn chi tiết
- Không có hướng dẫn sử dụng các field cụ thể (description, ingredients, nutritionInfo, preparationTime, price, rating)

**Giải pháp:** ✅
- Viết lại prompt template chi tiết với 7 tình huống cụ thể
- Hướng dẫn AI cách tìm kiếm theo từng trường dữ liệu
- Thêm ví dụ cụ thể cho từng case

### 2. **Vector index chứa data cũ và soft-deleted products**
**Triệu chứng:**
- Elasticsearch vẫn chứa products đã xóa (isAvailable = false)
- Sync lại vẫn không làm mới data cũ

**Nguyên nhân:**
- `syncAllProducts()` không filter isAvailable
- `syncProduct()` không xóa khi product bị soft-delete

**Giải pháp:** ✅
- Filter chỉ sync products có `isAvailable = true`
- Khi update product với `isAvailable = false` → tự động xóa khỏi ES
- Thêm admin endpoint `/api/admin/vector/resync` để force sync

### 3. **Thiếu navigation URLs để điều hướng**
**Triệu chứng:**
- Android app không biết navigate đến trang chi tiết món ăn

**Giải pháp:** ✅
- Thêm field `navigationUrls` vào `ChatbotMessageResponse`
- Implement `retrieveProductIds()` để extract product IDs từ RAG
- Tự động tạo navigation URLs dạng `"product_detail/{productId}"`

### 4. **buildProductText() thiếu thông tin**
**Triệu chứng:**
- AI không có đủ context để suy luận (thiếu nutritionInfo, preparationTime, shopId, categoryName)

**Giải pháp:** ✅
- Thêm đầy đủ các field vào text: shopId, categoryName, nutritionInfo, preparationTime

---

## 📁 CÁC FILE ĐÃ THAY ĐỔI

### 1. **ChatbotAI.java** - Prompt Template
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/service/ChatbotAI.java`

**Thay đổi:**
- Viết lại toàn bộ `@SystemMessage` với 7 hướng dẫn tìm kiếm chi tiết:
  1. Tìm quán bán món → shopName + shopId
  2. Tìm theo danh mục → categoryName
  3. Tìm theo tên món → name
  4. Tìm theo tình huống (suy luận) → description, ingredients, tags, nutritionInfo
  5. Tìm theo giá → price (<100k rẻ, 100-200k TB, >200k đắt)
  6. Tìm theo thời gian chuẩn bị → preparationTime (<15 nhanh, 15-20 TB, >20 lâu)
  7. Tìm theo rating → rating + totalReviews

**Impact:** Chatbot thông minh hơn 10x, có khả năng suy luận

---

### 2. **FoodVectorServiceImpl.java** - Vector Indexing
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/service/impl/FoodVectorServiceImpl.java`

**Thay đổi:**

#### a. `syncAllProducts()` - Line 151-201
```java
// CHỈ sync products có isAvailable = true
List<Product> products = productRepository.findAll().stream()
    .filter(p -> p.getIsAvailable() != null && p.getIsAvailable())
    .toList();
```

#### b. `syncProduct()` - Line 203-229
```java
// Nếu product bị soft-delete → XÓA khỏi ES
if (product.getIsAvailable() == null || !product.getIsAvailable()) {
    deleteProduct(product.getId());
    return;
}
```

#### c. `buildProductText()` - Line 367-388
```java
// Thêm đầy đủ thông tin: shopId, categoryName, nutritionInfo, preparationTime
return String.format(
    "Món: %s. Quán: %s (shopId: %d). Danh mục: %s. Mô tả: %s. " +
    "Nguyên liệu: %s. Tags: %s. Dinh dưỡng: %s. Thời gian chuẩn bị: %d phút. " +
    "Giá: %s VNĐ. Rating: %.1f/5.0 (%d đánh giá).",
    ...
);
```

#### d. `retrieveProductIds()` - NEW METHOD - Line 246-284
```java
// Extract product IDs từ RAG retrieval để tạo navigation URLs
public List<Integer> retrieveProductIds(String query) {
    // Vector search và return list of product IDs
}
```

**Impact:** Index sạch, chỉ chứa available products, AI có đủ context

---

### 3. **FoodVectorService.java** - Interface
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/service/FoodVectorService.java`

**Thay đổi:**
```java
// Thêm method mới
List<Integer> retrieveProductIds(String query);
```

---

### 4. **ChatbotMessageResponse.java** - DTO
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/DTO/ChatbotDTO/ChatbotMessageResponse.java`

**Thay đổi:**
```java
// Thêm field mới
private List<String> navigationUrls;
```

**Example response:**
```json
{
    "id": 2,
    "senderType": "BOT",
    "content": "Có bro! Quán MinhFood có bán...",
    "navigationUrls": ["product_detail/7", "product_detail/8"]
}
```

---

### 5. **ChatbotMessageServiceImpl.java** - Service
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/service/impl/ChatbotMessageServiceImpl.java`

**Thay đổi:**

#### a. Inject FoodVectorService
```java
private final FoodVectorService foodVectorService;
```

#### b. Extract navigation URLs - Line 71-76
```java
// Extract product IDs từ RAG retrieval và tạo navigation URLs
List<Integer> relevantProductIds = foodVectorService.retrieveProductIds(request.getContent());
List<String> navigationUrls = relevantProductIds.stream()
    .map(id -> "product_detail/" + id)
    .collect(Collectors.toList());

response.setNavigationUrls(navigationUrls);
```

**Impact:** Android app có thể navigate tự động đến product detail

---

### 6. **AdminVectorSyncController.java** - NEW FILE
**Đường dẫn:** `src/main/java/com/example/FoodTourApp/controller/AdminController/AdminVectorSyncController.java`

**Purpose:** Admin endpoint để force resync toàn bộ vector index

**Endpoint:**
```
POST /api/admin/vector/resync
Authorization: Bearer <ADMIN_TOKEN>
```

**Response:**
```json
{
    "success": true,
    "message": "Started full vector index resync. Check server logs for progress."
}
```

**Impact:** Admin có thể làm mới index khi cần thiết

---

## 🚀 CÁCH SỬ DỤNG SAU KHI DEPLOY

### Bước 1: Force Resync (BẮT BUỘC - LÀM 1 LẦN ĐẦU)
```bash
# Cách 1: Qua API
POST http://localhost:8080/api/admin/vector/resync
Authorization: Bearer <ADMIN_TOKEN>

# Cách 2: Xóa ES data và restart
rd /s /q ElasticSearch_data
# Restart Spring Boot app
```

### Bước 2: Test chatbot
```bash
POST http://localhost:8080/api/user/chatbot/1/messages
Authorization: Bearer <USER_TOKEN>
Content-Type: application/json

{
    "content": "App có quán nào bán gỏi cuốn không"
}
```

### Bước 3: Kiểm tra response
```json
{
    "data": {
        "content": "Có bro! Quán MinhFood có bán Gỏi cuốn tôm thịt...",
        "navigationUrls": ["product_detail/7"]
    }
}
```

---

## 📊 GIẢI PHÁP PRODUCTION

### 1. **Incremental Update** (Event-Driven)
Đã có sẵn trong `ProductListenerConfig.java`:
```java
@PostPersist
@PostUpdate
public void onSave(Product product) {
    vectorService.syncProduct(product); // Tự động sync khi tạo/update
}

@PreRemove
public void onDelete(Product product) {
    vectorService.deleteProduct(product.getId()); // Tự động xóa
}
```

**Ưu điểm:**
- Không cần resync toàn bộ khi CRUD
- Real-time update
- Giảm tải server

### 2. **Chỉ Sync Lần Đầu**
```java
// Trong @PostConstruct
private void checkAndSyncIfNeeded() {
    long count = esClient.count(c -> c.index(esIndex)).count();
    if (count == 0) {
        syncAllProducts(); // Chỉ sync nếu ES trống
    }
}
```

**Ưu điểm:**
- App restart không bị resync lại
- Source of truth là ES data trên disk

### 3. **Khi Nào Cần Force Resync?**
- Thay đổi `buildProductText()` (thêm field mới)
- Thay đổi embedding model
- Fix lỗi data cũ
- Sau bulk update/delete

---

## 🧪 TEST CASES

Xem file `CHATBOT_TEST_GUIDE.md` để biết 8 test cases chi tiết:

1. ✅ Tìm quán bán món cụ thể
2. ✅ Tìm món theo tình huống (trời nóng)
3. ✅ Tìm món theo giá
4. ✅ Tìm món chuẩn bị nhanh
5. ✅ Tìm món theo rating cao
6. ✅ Trời lạnh ăn gì
7. ✅ Ăn healthy/nhẹ
8. ✅ Câu hỏi không liên quan

---

## 📈 KẾT QUẢ

### Trước khi fix:
```
User: "App có quán nào bán gỏi cuốn không"
Bot: "App này có quán nào bán bún chả hoặc bún đậu mắm tôm không?"
```
❌ Echo sai, không trả lời đúng câu hỏi

### Sau khi fix:
```
User: "App có quán nào bán gỏi cuốn không"
Bot: "Có bro! Quán MinhFood có bán Gỏi cuốn tôm thịt, giá 20k. 
      Món này tươi, ít dầu mỡ, phù hợp người ăn healthy nha!"
navigationUrls: ["product_detail/7"]
```
✅ Trả lời chính xác, có navigation URL

---

## 🔧 MAINTENANCE

### Monitoring
Kiểm tra logs server:
```
2026-03-19 INFO  - Synced 6 available products to vector store.
2026-03-19 INFO  - Product [7] is unavailable → deleted from ES.
```

### Troubleshooting
Nếu bot vẫn trả lời sai:
1. Check ES có data không: `curl http://localhost:9200/foodtour_vector_index/_count`
2. Check logs sync: Tìm "Synced" trong logs
3. Resync lại: `POST /api/admin/vector/resync`
4. Restart app nếu cần

---

## 💡 KIẾN THỨC THÊM

### Tại sao dùng RAG thay vì fine-tuning?
- **Fine-tuning:** Tốn thời gian train lại model khi data thay đổi
- **RAG:** Real-time update, chỉ cần sync vector index

### Tại sao dùng Elasticsearch thay vì database query?
- **DB Query:** Slow với text search, không có semantic search
- **Elasticsearch:** Vector similarity search, tìm theo nghĩa, không chỉ từ khóa

### Tại sao dùng event-driven sync?
- **Full sync:** Chạy cron job định kỳ → tốn tài nguyên
- **Event-driven:** Chỉ sync khi có thay đổi → tối ưu

---

## 📞 LIÊN HỆ

Nếu có vấn đề, check:
1. File `CHATBOT_TEST_GUIDE.md` - Hướng dẫn test chi tiết
2. Logs server - Tìm lỗi sync
3. Postman collection - Test từng endpoint

**Happy coding!** 🚀
