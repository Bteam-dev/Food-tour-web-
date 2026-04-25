# FoodTour Chatbot - AI Food Assistant

Chatbot trợ lý món ăn thông minh, sử dụng RAG (Retrieval-Augmented Generation) kết hợp Google Gemini API + Elasticsearch để gợi ý món ăn chính xác từ dữ liệu thực trong database.

---

## Kiến trúc tổng quan

```
User gửi tin nhắn
    │
    ▼
ChatbotController (REST API)
    │
    ▼
ChatbotMessageServiceImpl
    │── Lưu tin nhắn user vào DB
    │── Gọi ChatbotAIServiceImpl.generateReply()
    │       │
    │       │── Load conversation memory (Caffeine cache, TTL 60 phút)
    │       │── LangChain4j AiServices gọi:
    │       │       │
    │       │       │── [1] ContentRetriever (RAG)
    │       │       │       │── Embed query bằng gemini-embedding-001 (3072 dims)
    │       │       │       │── Hybrid search trên Elasticsearch:
    │       │       │       │     BM25 text match (30%) + cosine similarity (70%)
    │       │       │       │     Fields: name^3, description, tags, ingredients,
    │       │       │       │            category_name^2, shop_name,
    │       │       │       │            shop_district^2, shop_city^2, shop_ward
    │       │       │       │── Lọc theo min-score, trả về top 6 sản phẩm
    │       │       │       │── Cache product IDs vào ThreadLocal
    │       │       │       ▼
    │       │       │── [2] Google Gemini 2.5 Flash
    │       │       │       │── System prompt + CONTEXT (sản phẩm) + user message
    │       │       │       ▼
    │       │       │── Trả về câu trả lời tự nhiên
    │       │
    │── Lưu tin nhắn bot vào DB
    │── Lấy product IDs từ cache (KHÔNG gọi ES lần 2)
    │── Query DB lấy thông tin sản phẩm đầy đủ
    │── Build product cards (tên, quán, giá, rating, ảnh)
    ▼
Response: { botReply, suggestedProducts[], navigationUrls[] }
```

---

## Tech Stack

| Component | Technology | Vai trò |
|-----------|-----------|---------|
| LLM (Chat) | Google Gemini 2.5 Flash | Sinh câu trả lời tự nhiên bằng tiếng Việt |
| Embedding | gemini-embedding-001 | Chuyển text thành vector 3072 chiều |
| Vector Search | Elasticsearch 8.11.1 | Lưu trữ + tìm kiếm vector + full-text |
| RAG Framework | LangChain4j 0.36.2 | Kết nối LLM + retriever + memory |
| Conversation Memory | Caffeine Cache | Lưu lịch sử hội thoại trong RAM (60 phút) |
| Event Queue | Redis | Đồng bộ sản phẩm vào ES theo thời gian thực |
| Database | MySQL 8.0 | Lưu hội thoại, tin nhắn, sản phẩm |
| Sync Script | Python + google-generativeai | Full reindex khi cần |

---

## API Endpoints

Base URL: `/api/user/chatbot`

**Yêu cầu xác thực**: Tất cả endpoint cần JWT token trong header `Authorization: Bearer <token>`

### Quản lý hội thoại

| Method | Endpoint | Request Body | Response | Mô tả |
|--------|----------|-------------|----------|-------|
| POST | `/conversations` | `{ "title": "Hỏi về món ngon" }` | `{ id, title, createdAt }` | Tạo hội thoại mới |
| GET | `/conversations` | - | `[{ id, title, createdAt, updatedAt }]` | Danh sách hội thoại (mới nhất trước) |
| PUT | `/conversations/{id}` | `{ "title": "Tên mới" }` | `{ id, title, ... }` | Đổi tên hội thoại |
| DELETE | `/conversations/{id}` | - | 200 OK | Xóa hội thoại + toàn bộ tin nhắn |

### Nhắn tin

| Method | Endpoint | Request Body | Response | Mô tả |
|--------|----------|-------------|----------|-------|
| POST | `/{conversationId}/messages` | `{ "content": "Món gì ngon ở SG?" }` | Xem chi tiết bên dưới | Gửi tin nhắn, nhận trả lời AI |
| GET | `/conversations/{id}/messages` | - | `[{ id, senderType, content, createdAt }]` | Lịch sử tin nhắn |

### Response format khi gửi tin nhắn

```json
{
  "id": 42,
  "senderType": "BOT",
  "senderName": "FoodTour Bot",
  "content": "Oi bro, nói đến món ngon Sài Gòn thì phải thử **Bánh Mì Thịt Nướng** ...",
  "createdAt": "2026-04-11T10:30:00",
  "navigationUrls": [
    "product_detail/15",
    "product_detail/23"
  ],
  "suggestedProducts": [
    {
      "productId": 15,
      "productName": "Bánh Mì Thịt Nướng",
      "shopName": "Bánh Mì Huỳnh Hoa",
      "shopId": 3,
      "price": 45000,
      "discountPrice": 39000,
      "rating": 4.7,
      "totalReviews": 328,
      "imageUrl": "https://..../banh-mi.jpg",
      "navigationUrl": "product_detail/15"
    }
  ]
}
```

---

## Hybrid Search (BM25 + Vector)

### Công thức scoring

```
final_score = bm25_score * 0.3 + (cosine_similarity + 1.0) * 0.7
```

### Các trường được search

| Field | Boost | Mục đích |
|-------|-------|----------|
| `name` | x3 | Tên món: "Phở Bò", "Bánh Mì" |
| `category_name` | x2 | Danh mục: "Món nước", "Đồ uống" |
| `shop_district` | x2 | Lọc theo quận: "Quận 1", "Tân Bình" |
| `shop_city` | x2 | Lọc theo thành phố: "Hồ Chí Minh" |
| `description` | x1 | Mô tả món ăn |
| `tags` | x1 | "cay", "healthy", "ăn khuya" |
| `ingredients` | x1 | Nguyên liệu |
| `shop_name` | x1 | Tên quán |
| `shop_ward` | x1 | Phường/Xã |

### Config

```properties
rag.max-results=6          # Trả về tối đa 6 sản phẩm
rag.min-score=1.1          # Ngưỡng điểm tối thiểu (hybrid score)
```

---

## Elasticsearch Index

### Index name: `foodtour_products_chatbot`

### Mapping

| Field | Type | Mô tả |
|-------|------|-------|
| `id` | integer | Product ID |
| `name` | text | Tên món ăn (BM25 searchable) |
| `description` | text | Mô tả món ăn |
| `ingredients` | text | Nguyên liệu |
| `nutrition_info` | text | Thông tin dinh dưỡng |
| `tags` | keyword | Tags ("cay", "healthy", "ăn khuya") |
| `shop_name` | text | Tên cửa hàng |
| `category_name` | text | Tên danh mục |
| `shop_address` | text | Địa chỉ đầy đủ của quán |
| `shop_ward` | keyword | Phường/Xã |
| `shop_district` | keyword | Quận/Huyện |
| `shop_city` | keyword | Thành phố |
| `price` | scaled_float | Giá gốc |
| `discount_price` | scaled_float | Giá khuyến mãi |
| `preparation_time` | integer | Thời gian chuẩn bị (phút) |
| `rating` | double | Điểm đánh giá trung bình |
| `total_reviews` | long | Tổng số lượt đánh giá |
| `is_available` | boolean | Còn bán không |
| `context_text` | text | Nội dung có cấu trúc cho RAG |
| `embedding` | dense_vector (3072 dims, cosine) | Vector embedding |

### Context text format (truyền vào LLM)

```
===PRODUCT===
Món: Phở Bò Tái Chín
Quán: Phở Thìn (shopId: 5)
Danh mục: Món nước
Địa chỉ quán: 13 Lò Đúc, Phường Phạm Đình Hổ, Quận Hai Bà Trưng, Hà Nội
Quận/Huyện: Quận Hai Bà Trưng
Thành phố: Hà Nội
Mô tả: Phở bò truyền thống Hà Nội, nước dùng ninh xương 12 tiếng
Nguyên liệu: Bánh phở, thịt bò tái, thịt bò chín, hành lá, rau mùi
Tags: truyền thống, món nước, sáng
Dinh dưỡng: 480 kcal, 25g protein, 45g carb
Thời gian chuẩn bị: 10 phút
Giá: 55000 VNĐ
Rating: 4.6/5.0 (215 đánh giá)
===END_PRODUCT===
```

---

## Đồng bộ dữ liệu (ES Sync)

### Tự động (Runtime) — mỗi khi product thay đổi

```
Sản phẩm thay đổi (thêm/sửa/xóa)
    │ JPA @PostPersist/@PostUpdate/@PostRemove
    ▼
ProductEntityListener
    │── pushIndexEvent(productId) → es:chatbot:sync:queue
    │── pushIndexEvent(productId) → es:search:sync:queue
    ▼
EsChatbotSyncConsumer (background, poll mỗi 5 giây)
    │── Pop batch 50 events
    │── Deduplicate theo productId
    │── Fetch sản phẩm từ DB
    │── Generate embedding (gemini-embedding-001, 3072 dims)
    │── Build context_text (bao gồm location)
    │── Bulk index vào Elasticsearch
```

### Thủ công (Full reindex) — dùng script

Cần chạy script khi:

| Tình huống | Cần reindex? |
|---|---|
| Thêm/sửa/xóa product thông thường | ❌ Incremental sync tự lo |
| Restart app | ❌ ES data vẫn còn trên disk |
| Sửa `buildContextText()` (thêm field mới) | ✅ Phải reindex |
| Đổi embedding model | ✅ Phải reindex |
| Fix index corrupt / data cũ sai | ✅ Phải reindex |
| Deploy lần đầu (ES index trống) | ✅ Phải reindex |
| Bulk update ngoài app (trực tiếp DB) | ✅ Phải reindex |

```bash
# Cài thư viện
pip install pymysql elasticsearch google-generativeai

# Set API key
export GEMINI_API_KEY=your_key_here

# Chạy script — xóa index cũ, tạo lại mapping, sync toàn bộ data
python scripts/chatbot/sync_es_chatbot.py
```

Script sẽ:
1. Xóa index cũ (nếu có) để tránh mapping conflict
2. Tạo index mới với mapping: dense_vector 3072 dims + location fields
3. Fetch toàn bộ sản phẩm từ MySQL (JOIN với shops để lấy địa chỉ)
4. Tạo context_text + generate embedding qua gemini-embedding-001
5. Bulk index vào Elasticsearch

---

## System Prompt

Bot được cấu hình với các nguyên tắc:

### Chống hallucination (QUAN TRỌNG NHẤT)
- **CHỈ** dùng thông tin trong CONTEXT được Elasticsearch trả về
- Không có CONTEXT → trả lời "Tiếc quá bro, món này app chưa có"
- KHÔNG bịa tên món, giá, rating, quán. KHÔNG trộn thông tin giữa các món
- Mỗi món gợi ý BẮT BUỘC có: **Tên món** (bold), quán (shopId), giá VNĐ, rating (số đánh giá)

### Phong cách trả lời
- Thân thiện, tự nhiên như bạn bè ("bro", "nha", "nè")
- Kể chuyện liền mạch, KHÔNG bullet point, KHÔNG số thứ tự
- Thêm cảm xúc ("ngon lắm bro", "rẻ bèo luôn")

### Suy luận địa điểm
- Khi hỏi "ở quận X/thành phố Y có ... không?" → đọc trường "Quận/Huyện:" và "Thành phố:" trong CONTEXT
- Chỉ gợi ý món có quán TẠI quận/thành phố đó. Không có → "Tiếc quá bro, app chưa có quán nào ở [địa điểm]..."
- Khi trả lời có địa chỉ → thêm "Quán nằm ở [Địa chỉ quán]"

### Suy luận thông minh
- Tên món cụ thể → match trường "Món:"
- Quán nào bán → lấy shopId + tên quán
- Danh mục → dùng "Danh mục:"
- Trời lạnh/nóng/khuya/healthy → đọc mô tả + tags + nguyên liệu + dinh dưỡng
- Giá: <100k = rẻ, 100-200k = vừa, >200k = cao cấp
- Thời gian: <15 phút = nhanh, 15-20 = vừa, >20 = lâu
- Top ngon: ưu tiên rating cao + nhiều đánh giá

---

## Conversation Memory

| Config | Giá trị | Mô tả |
|--------|---------|-------|
| Cache type | Caffeine | In-memory, tự động dọn dẹp |
| Max conversations | 500 | Tối đa 500 hội thoại đồng thời trong RAM |
| Expire | 60 phút | Không hoạt động 60 phút → xóa khỏi RAM |
| Message window | 30 tin nhắn | LLM chỉ nhận 30 tin gần nhất làm context |
| Persistence | MySQL | Toàn bộ tin nhắn lưu DB, load lại khi cần |

**Flow khởi tạo memory:**
1. User gửi tin nhắn trong conversation
2. Kiểm tra Caffeine cache có conversation không
3. Nếu KHÔNG có → load 30 tin nhắn gần nhất từ DB → nạp vào memory
4. Nếu CÓ → dùng memory sẵn có
5. Sau 60 phút không hoạt động → tự động xóa khỏi RAM

---

## Tối ưu hiệu suất

### 1. ThreadLocal cache (tránh double embedding)

**Vấn đề:** Mỗi tin nhắn gọi Elasticsearch 2 lần:
- Lần 1: RAG ContentRetriever (lấy context cho LLM)
- Lần 2: `retrieveProductIds()` (lấy product IDs cho card UI)

**Giải pháp:** ContentRetriever cache product IDs vào ThreadLocal → `retrieveProductIds()` đọc từ cache.

```
Trước: 2 lần embedding + 2 lần ES query = ~2s
Sau:   1 lần embedding + 1 lần ES query = ~1s (nhanh gấp đôi)
```

### 2. Caffeine cache cho conversation memory

Không cần load lịch sử từ DB mỗi lần gửi tin nhắn. Chỉ load 1 lần, sau đó dùng memory trong RAM.

### 3. Background sync (Redis event queue)

Sản phẩm thay đổi → push event vào Redis → consumer xử lý batch 50 events mỗi 5 giây. Không ảnh hưởng tới API response time.

---

## Cấu hình

### application.properties

```properties
# Google Gemini API (free: 15 RPM, 1500 req/ngày)
gemini.api-key=${GEMINI_API_KEY}
gemini.model=gemini-2.5-flash
gemini.embedding-model=gemini-embedding-001

# Elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.index=foodtour_products_chatbot

# RAG
rag.max-results=6       # Số sản phẩm tối đa trả về
rag.min-score=1.1        # Ngưỡng điểm hybrid search
```

### Free tier Google Gemini

| Giới hạn | Giá trị |
|----------|---------|
| Requests/phút | 15 |
| Requests/ngày | 1,500 |
| Tokens/phút | 1,000,000 |
| Giá | **Miễn phí** |

Đủ cho dev/demo. Production cần billing (rất rẻ: ~$0.10/1M tokens).

---

## Files liên quan

```
src/main/java/com/example/FoodTourApp/
├── config/
│   └── AiConfig.java                          # Bean GoogleAiGeminiChatModel
├── controller/UserController/
│   └── ChatbotController.java                 # REST API endpoints
├── DTO/ChatbotDTO/
│   ├── CreateChatbotConversationRequest.java
│   ├── SendChatbotMessageRequest.java
│   ├── ChatbotConversationResponse.java
│   └── ChatbotMessageResponse.java
├── entity/
│   ├── ChatbotConversation.java
│   └── ChatbotMessage.java
├── repository/
│   ├── ChatbotConversationRepository.java
│   └── ChatbotMessageRepository.java
├── service/
│   ├── ChatbotAI.java                          # LangChain4j interface + system prompt
│   ├── ChatbotAIService.java
│   ├── ChatbotMessageService.java
│   ├── FoodVectorService.java
│   ├── EsChatbotSyncProducer.java
│   └── impl/
│       ├── ChatbotAIServiceImpl.java           # Memory management + LLM orchestration
│       ├── ChatbotMessageServiceImpl.java      # Message flow + product cards
│       ├── FoodVectorServiceImpl.java          # Hybrid search + embedding + cache
│       ├── EsChatbotSyncConsumer.java          # Background ES sync (Redis → ES)
│       └── EsChatbotSyncProducerImpl.java      # Push sync events to Redis

scripts/chatbot/
└── sync_es_chatbot.py   # Full reindex: xóa index cũ → tạo lại mapping → sync toàn bộ
```

---

## Troubleshooting

| Vấn đề | Nguyên nhân | Giải pháp |
|--------|------------|-----------|
| Bot trả lời "app chưa có dữ liệu" | ES index rỗng | Chạy `python scripts/chatbot/sync_es_chatbot.py` |
| Bot không biết quán ở đâu | Index cũ thiếu location fields | Chạy lại `sync_es_chatbot.py` (tạo lại index với mapping mới) |
| Bot trả lời chậm | Gemini API latency | Bình thường ~1-3s, kiểm tra mạng |
| Lỗi 429 (rate limit) | Vượt 15 RPM free tier | Đợi 1 phút hoặc nâng cấp billing |
| Bot bịa thông tin | min-score quá thấp, CONTEXT không liên quan | Tăng `rag.min-score` |
| Tìm không ra món | min-score quá cao | Giảm `rag.min-score` |
| Embedding dimension mismatch | Đổi model mà chưa rebuild index | Chạy `sync_es_chatbot.py` (xóa index + tạo lại mapping) |
| ES connection refused | Elasticsearch chưa chạy | `docker-compose up -d elasticsearch` |
