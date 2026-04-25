# CƠ CHẾ SYNC VÀ RESPONSE CỦA CHATBOT

## 1. KIẾN TRÚC TỔNG QUAN

```
┌─────────────────────────────────────────────────────────────────┐
│                       CRUD THAY ĐỔI PRODUCT                      │
│   (thêm/sửa/xóa qua Seller API hoặc Admin API)                   │
└──────────────────────────────┬──────────────────────────────────┘
                               │ JPA fires @PostPersist / @PostUpdate / @PostRemove
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│              ProductEntityListener (config/)                      │
│   @PostPersist + @PostUpdate → pushIndexEvent(productId)         │
│   @PostRemove               → pushDeleteEvent(productId)         │
└──────────────────────────────┬──────────────────────────────────┘
                               │
              ┌────────────────┴────────────────┐
              ▼                                  ▼
 esChatbotSyncProducer                esProductSyncProducer
 rightPush("es:chatbot:sync:queue")   rightPush("es:search:sync:queue")
              │
              ▼
┌─────────────────────────────────────────────────────────────────┐
│           Redis Queue: es:chatbot:sync:queue                      │
│   Stores: ProductSyncEvent { productId, action=INDEX/DELETE }    │
└──────────────────────────────┬──────────────────────────────────┘
                               │ poll mỗi 5 giây (fixedDelay=5000ms)
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│              EsChatbotSyncConsumer (@Scheduled)                   │
│                                                                   │
│  1. leftPop batch (tối đa 50 events)                             │
│  2. Deduplicate by productId (latest event wins)                 │
│  3. Fetch products from DB (batch findAllById)                   │
│  4. Generate Gemini embedding (gemini-embedding-001, 3072 dim)   │
│  5. Build context_text (bao gồm shop location fields)            │
│  6. ES bulk index → foodtour_products_chatbot                    │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. INCREMENTAL SYNC (Tự động, real-time)

### Cách hoạt động

Mỗi khi Product thay đổi trong DB, JPA tự động trigger `ProductEntityListener`:

```java
// ProductEntityListener.java
@PostPersist
@PostUpdate
public void onSaveOrUpdate(Product product) {
    esChatbotSyncProducer.pushIndexEvent(product.getId());  // → es:chatbot:sync:queue
    esProductSyncProducer.pushIndexEvent(product.getId());  // → es:search:sync:queue
}

@PostRemove
public void onDelete(Product product) {
    esChatbotSyncProducer.pushDeleteEvent(product.getId());
    esProductSyncProducer.pushDeleteEvent(product.getId());
}
```

### Latency

- Event đưa vào Redis queue: **< 5ms** (non-blocking)
- Consumer xử lý: trong vòng **≤ 5 giây** (poll interval)
- Tổng thời gian từ thay đổi DB đến chatbot biết: **≤ 5-10 giây** (bao gồm Gemini API call)

### Ví dụ: Seller thêm món "Cơm gà xối mỡ"

```
1. POST /api/seller/shop/5/products
   Body: { "name": "Cơm gà xối mỡ", "price": 45000, ... }

2. productRepository.save(comGa) → @PostPersist fires

3. pushIndexEvent(productId=39) → Redis: es:chatbot:sync:queue

4. (≤5s) EsChatbotSyncConsumer polls, pops event

5. Fetch product 39 + shop info từ DB
   → buildContextText() → "===PRODUCT=== Món: Cơm gà xối mỡ ... Quận/Huyện: Quận 3 ..."
   → generateEmbedding(contextText) → float[3072] via Gemini

6. ES bulk index { id: 39, context_text: ..., shop_district: ..., embedding: [...] }
   → foodtour_products_chatbot

7. Chatbot giờ biết món "Cơm gà xối mỡ" + vị trí quán ✅
```

### Ví dụ: Seller sửa giá "Phở bò" từ 50k → 35k

```
1. PUT /api/seller/shop/1/products/4
   Body: { "price": 35000 }

2. productRepository.save(pho) → @PostUpdate fires

3. pushIndexEvent(4) → queue

4. (≤5s) Consumer pops, re-index product 4 với giá mới

5. Chatbot trả lời: "Phở bò giá 35.000 VNĐ" ✅
```

### Ví dụ: Admin xóa sản phẩm

```
1. DELETE /api/admin/products/10

2. productRepository.delete(product) → @PostRemove fires

3. pushDeleteEvent(10) → queue

4. (≤5s) Consumer pops DELETE event
   → ES bulk delete document id=10

5. Chatbot không còn recommend sản phẩm đó ✅
```

---

## 3. FULL REINDEX (Thủ công, dùng script)

### Khi nào cần Full Reindex?

| Tình huống | Cần reindex? |
|---|---|
| Thêm/sửa/xóa product thông thường | ❌ Incremental sync tự lo |
| Restart app | ❌ ES data vẫn còn trên disk |
| Sửa `buildContextText()` (thêm field mới) | ✅ Phải reindex |
| Đổi embedding model | ✅ Phải reindex |
| Fix index corrupt / data cũ sai | ✅ Phải reindex |
| Deploy lần đầu (ES index trống) | ✅ Phải reindex |
| Bulk update ngoài app (trực tiếp DB) | ✅ Phải reindex |

### Cách chạy

```bash
# Cài thư viện (chỉ lần đầu)
pip install pymysql elasticsearch google-generativeai

# Set API key
export GEMINI_API_KEY=your_key_here   # Windows: set GEMINI_API_KEY=your_key_here

# Chạy script
python scripts/chatbot/sync_es_chatbot.py
```

### Script làm gì?

```
1. Xóa index cũ 'foodtour_products_chatbot' (nếu tồn tại)
   → Tránh mapping conflict khi thêm fields mới

2. Tạo index mới với mapping chính xác:
   → dense_vector 3072 dims + cosine similarity
   → Location fields: shop_address, shop_ward, shop_district, shop_city

3. Fetch toàn bộ sản phẩm is_available=true từ MySQL
   → JOIN với shops để lấy địa chỉ đầy đủ

4. Với mỗi sản phẩm:
   → Build context_text (format khớp với EsChatbotSyncConsumer)
   → Generate embedding 3072 chiều qua gemini-embedding-001
   → Bulk index vào ES

5. Kết thúc → chatbot sẵn sàng với dữ liệu mới
```

**Lưu ý:** Với 40 products + Gemini free tier (15 RPM), script mất khoảng **3-5 phút**. Script tự động throttle, không cần lo.

---

## 4. CACHE TRONG CHATBOT

### 4.1 Caffeine Cache - Conversation Memory

```java
// ChatbotAIServiceImpl.java
private static final int MEMORY_WINDOW_SIZE = 30;

private final Cache<Long, ChatbotAI> conversationBots = Caffeine.newBuilder()
        .expireAfterAccess(60, TimeUnit.MINUTES)  // Hết hạn sau 60 phút không dùng
        .maximumSize(500)                          // Tối đa 500 conversation đồng thời
        .build();
```

- **Mục đích**: Lưu bot instance (chat history + context) cho mỗi conversation trong RAM
- **Key**: `conversationId` (Long)
- **Giá trị**: `ChatbotAI` instance (LangChain4j AiServices với `MessageWindowChatMemory`)
- **Tự động xóa**: sau 60 phút idle, hoặc khi cache đầy (LRU evict conversation ít dùng nhất)

#### Khi cache bị xóa, có tự động khôi phục không?

**Có.** Logic nằm trong `getOrCreateBot()`:

```java
private ChatbotAI getOrCreateBot(Long conversationId) {
    return conversationBots.get(conversationId, id -> {
        // Cache miss → load lại lịch sử từ MySQL (tối đa 30 tin gần nhất)
        List<ChatbotMessage> history = messageRepository
                .findByConversation_IdOrderByCreatedAtAsc(id);

        ChatMemory memory = MessageWindowChatMemory.withMaxMessages(MEMORY_WINDOW_SIZE);

        int start = Math.max(0, history.size() - MEMORY_WINDOW_SIZE);
        for (int i = start; i < history.size(); i++) {
            ChatbotMessage msg = history.get(i);
            if ("USER".equals(msg.getSenderType())) {
                memory.add(UserMessage.from(msg.getContent()));
            } else {
                memory.add(AiMessage.from(msg.getContent()));
            }
        }

        log.info("[Chatbot] Restored {} messages from DB for conversation {}",
                history.size() - start, id);

        return AiServices.builder(ChatbotAI.class)
                .chatLanguageModel(chatModel)
                .contentRetriever(vectorService.getContentRetriever())
                .chatMemory(memory)
                .build();
    });
}
```

**MySQL là nguồn sự thật.** Caffeine chỉ là lớp RAM để tránh query DB mỗi tin nhắn. Khi cache miss, bot mới được tạo với context đã được nạp lại từ DB — user không cảm nhận được gì.

#### Các tình huống cụ thể

| Tình huống | Kết quả |
|---|---|
| User nhắn tin, conversation đang trong cache | Dùng bot trong RAM, không đụng DB |
| User quay lại sau 60 phút idle | Cache miss → load lại từ DB → restore bình thường ✅ |
| App restart (JVM chết) | Toàn bộ Caffeine cache mất → load lại từ MySQL khi dùng ✅ |
| Conversation có 100 tin, cache bị evict | Chỉ restore **30 tin gần nhất** (MEMORY_WINDOW_SIZE) |
| 501 conversation đồng thời | LRU evict conversation ít dùng nhất → nếu user đó nhắn tin lại thì load từ DB |
| Conversation bị xóa (`DELETE /conversations/{id}`) | `removeConversationMemory()` invalidate cache + DB xóa hết → không restore được ❌ |

### 4.2 ThreadLocal Cache - RAG Product IDs

```java
// FoodVectorServiceImpl.java
private final ThreadLocal<List<Integer>> lastRetrievedProductIds = new ThreadLocal<>();
```

- **Mục đích**: Tránh gọi ES 2 lần trong cùng một request
- **Flow**:
  1. `buildContentRetriever()` gọi ES → lưu product IDs vào ThreadLocal
  2. `ChatbotMessageServiceImpl` gọi `retrieveProductIds()` → lấy từ ThreadLocal (không gọi ES nữa)
  3. ThreadLocal bị clear sau mỗi request (`.remove()` được gọi)
- **Lợi ích**: Tiết kiệm 1 lần Gemini embedding + 1 lần ES query mỗi message

---

## 5. RAG - CHATBOT TÌM KIẾM SẢN PHẨM

### Flow khi user gửi tin nhắn

```
User: "quận 3 có quán bún bò ngon không?"
         │
         ▼
ChatbotAIServiceImpl
  → getOrCreateBot(conversationId)
  → bot.chat(message)
         │
         ▼ LangChain4j RAG
FoodVectorServiceImpl.buildContentRetriever()
  1. Gemini embed("quận 3 có quán bún bò ngon không?")
     → float[3072] query vector
  2. ES hybrid query:
     BM25 multi_match (name, description, shop_district^2, shop_city^2...) * 0.3
     + cosine_similarity(query_vector, doc.embedding) * 0.7
  3. Filter: score >= minScore (1.1)
  4. Return top N products as context_text
  5. Cache product IDs in ThreadLocal
         │
         ▼
Gemini 2.5 Flash generates response
  Input: [system prompt] + [context_text từ RAG] + [chat history] + [user message]
  → Đọc "Quận/Huyện:" trong context → chỉ gợi ý quán ở Quận 3
  Output: "Ở Quận 3 có **Bún Bò Huế Mụ Rua** tại 28 Võ Thị Sáu, giá 45k..."
         │
         ▼
ChatbotMessageServiceImpl
  → retrieveProductIds() từ ThreadLocal (không gọi ES lại)
  → Build suggestedProducts list
  → Save message to DB
  → Return ChatbotMessageResponse
```

---

## 6. RESPONSE FORMAT

```json
{
    "id": 42,
    "senderType": "BOT",
    "senderName": "FoodTour Bot",
    "content": "Ở Quận 3 có **Bún Bò Huế Mụ Rua** tại 28 Võ Thị Sáu, giá 45k, rating 4.8. Thử xem nhé bro!",
    "createdAt": "2026-04-16T10:30:00",
    "navigationUrls": ["product_detail/8"],
    "suggestedProducts": [
        {
            "productId": 8,
            "productName": "Bún Bò Huế Mụ Rua",
            "shopName": "Quán Mụ Rua",
            "shopId": 1,
            "price": 45000.00,
            "discountPrice": null,
            "rating": 4.8,
            "totalReviews": 185,
            "imageUrl": "/uploads/ProductImage/...",
            "navigationUrl": "product_detail/8"
        }
    ]
}
```

---

## 7. TÓM TẮT

| | Incremental | Full Reindex |
|---|---|---|
| **Trigger** | Tự động (JPA listener) | Thủ công (Python script) |
| **Phạm vi** | 1 product | Tất cả products |
| **Latency** | ≤ 5-10 giây | ~3-5 phút (Gemini rate limit) |
| **Pipeline** | Redis queue → Consumer → Gemini → ES | Script: MySQL → Gemini → ES |
| **Khi nào dùng** | Mọi CRUD thường ngày | Sửa code logic / fix index / đổi mapping |

**Luồng chính:** Product change → JPA Listener → Redis queue → Consumer (5s poll) → Gemini embedding → ES bulk index → Chatbot cập nhật tự động.
