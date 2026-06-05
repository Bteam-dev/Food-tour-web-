# TÀI LIỆU BÁO CÁO: CHATBOT AI (RAG)
> Google Gemini 2.5 Flash + LangChain4j + Elasticsearch
> RAG (Retrieval-Augmented Generation) | Hybrid Search BM25 + Cosine 3072-dim

---

## 1. TỔNG QUAN

### Mục tiêu
Chatbot trợ lý món ăn thông minh:
- Trả lời câu hỏi về món ăn, quán, địa điểm dựa trên dữ liệu thực từ database
- Gợi ý sản phẩm kèm navigation link trực tiếp trong app
- Chống hallucination: chỉ dùng dữ liệu từ Elasticsearch, không bịa thông tin

### Tech Stack

| Component | Technology | Vai trò |
|---|---|---|
| LLM | Google Gemini 2.5 Flash | Sinh câu trả lời tự nhiên tiếng Việt |
| Embedding | gemini-embedding-001 (3072 dims) | Chuyển query/document → vector |
| Vector DB | Elasticsearch 8.11.1 | Lưu + tìm kiếm vector + full-text |
| RAG Framework | LangChain4j 0.36.2 | Kết nối LLM + Retriever + Memory |
| Conversation Memory | Caffeine Cache | RAM cache, TTL 60 phút, max 500 sessions |
| Event Queue | Redis (es:chatbot:sync:queue) | Sync sản phẩm mới vào ES |
| Database | MySQL 8.0 | Lưu conversations + messages |

---

## 2. KIẾN TRÚC VÀ PIPELINE

### 2.1 Tổng quan pipeline

```
User gửi tin nhắn
    │
    ▼
ChatbotController
  POST /api/user/chatbot/{conversationId}/messages
    │ Requires: JWT token
    ▼
ChatbotMessageServiceImpl.sendMessage()
    │
    ├── 1. Lưu user message vào MySQL
    │
    ├── 2. ChatbotAIServiceImpl.generateReply()
    │       │
    │       ├── Load conversation memory (Caffeine cache)
    │       │   ├── Cache HIT: dùng memory trong RAM
    │       │   └── Cache MISS: load 30 tin gần nhất từ MySQL → nạp vào memory
    │       │
    │       └── LangChain4j AiServices.chat():
    │               │
    │               ├── [1] ContentRetriever (RAG)
    │               │       ├── Embed query bằng gemini-embedding-001
    │               │       │   (3072-dim dense vector)
    │               │       ├── Hybrid search Elasticsearch:
    │               │       │   BM25 (30%) + cosine similarity (70%)
    │               │       │   Fields: name^3, category^2, district^2, city^2, desc^1
    │               │       │   Filter: is_available=true, min_score=1.1
    │               │       │   → Top 6 sản phẩm
    │               │       └── Cache product IDs vào ThreadLocal
    │               │
    │               └── [2] Gemini 2.5 Flash
    │                       Input: System Prompt + CONTEXT (products) + conversation history
    │                       Output: Câu trả lời tự nhiên
    │
    ├── 3. Lưu bot message vào MySQL
    │
    ├── 4. Lấy product IDs từ ThreadLocal cache (KHÔNG gọi ES lần 2)
    │
    └── 5. Query MySQL lấy product details → build product cards
    
    ▼
Response: { botReply, suggestedProducts[], navigationUrls[] }
```

### 2.2 ThreadLocal Cache — Tối ưu hiệu suất

```
VẤN ĐỀ: Mỗi tin nhắn cần ES 2 lần:
  - Lần 1: RAG ContentRetriever lấy context cho LLM
  - Lần 2: retrieveProductIds() lấy IDs cho product cards UI

GIẢI PHÁP: ThreadLocal<List<Long>>
  ContentRetriever → lấy xong → cache productIds vào ThreadLocal
  ChatbotMessageServiceImpl → đọc từ ThreadLocal, KHÔNG gọi ES lại

KẾT QUẢ:
  Trước: 2 lần embedding (1400ms) + 2 lần ES query = ~2s
  Sau:   1 lần embedding (700ms) + 1 lần ES query = ~1s (nhanh 2x)
```

---

## 3. ELASTICSEARCH INDEX

### Index: `foodtour_products_chatbot`

| Field | Type | Vai trò |
|---|---|---|
| `id` | integer | Product ID |
| `name` | text | Tên món (BM25 searchable) |
| `description` | text | Mô tả món ăn |
| `ingredients` | text | Nguyên liệu |
| `tags` | keyword | Tags: "cay", "healthy", "ăn khuya" |
| `shop_name` | text | Tên quán |
| `category_name` | text | Tên danh mục |
| `shop_district` | keyword | Quận/Huyện (có boost x2) |
| `shop_city` | keyword | Thành phố (có boost x2) |
| `shop_ward` | keyword | Phường/Xã |
| `shop_address` | text | Địa chỉ đầy đủ |
| `price` | scaled_float | Giá gốc |
| `discount_price` | scaled_float | Giá khuyến mãi |
| `rating` | double | Điểm đánh giá |
| `total_reviews` | long | Số lượt đánh giá |
| `preparation_time` | integer | Thời gian chuẩn bị (phút) |
| `context_text` | text | Nội dung có cấu trúc cho LLM |
| `embedding` | dense_vector (3072, cosine) | Vector embedding |

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

## 4. HYBRID SEARCH — BM25 + COSINE

### Công thức scoring

```
final_score = bm25_score × 0.3 + (cosine_similarity + 1.0) × 0.7
```

**Tại sao cộng 1.0 vào cosine?**
Cosine similarity ∈ [-1, 1]. Cộng 1.0 → [0, 2] để không có giá trị âm khi blend với BM25.

### Field boost

| Field | Boost | Lý do |
|---|---|---|
| `name` | x3 | Tên món là thông tin quan trọng nhất |
| `category_name` | x2 | Danh mục giúp filter chính xác |
| `shop_district` | x2 | Lọc địa điểm — user hay hỏi "ở quận X" |
| `shop_city` | x2 | Lọc thành phố |
| `description`, `tags`, `ingredients`, `shop_name`, `shop_ward` | x1 | Hỗ trợ |

### Config

```properties
rag.max-results=6     # Trả về tối đa 6 sản phẩm cho LLM
rag.min-score=1.1     # Ngưỡng điểm tối thiểu hybrid score
                      # Tăng → ít kết quả nhưng chính xác hơn
                      # Giảm → nhiều kết quả nhưng có thể không liên quan
```

---

## 5. CONVERSATION MEMORY

```
Caffeine Cache:
  MAX_CONVERSATIONS = 500  (đồng thời trong RAM)
  TTL = 60 phút (inactive)
  MESSAGE_WINDOW = 30 tin nhắn gần nhất (context window LLM)
  
Flow:
  1. User gửi tin → check Caffeine cache by conversationId
  2. MISS → load 30 tin gần nhất từ MySQL → store vào cache
  3. HIT → append tin nhắn mới vào in-memory list
  4. Pass [system_prompt + memory + new_message + CONTEXT] → Gemini
  5. Store bot response vào cache + MySQL
```

**Tại sao giới hạn 30 tin nhắn?**
- Gemini 2.5 Flash context window: 1M tokens — đủ cho 30 tin
- Nhiều hơn: API cost tăng, latency tăng, không cần thiết
- Tất cả tin vẫn lưu MySQL — user có thể scroll xem lịch sử đầy đủ

---

## 6. SYSTEM PROMPT — CHỐNG HALLUCINATION

```
NGUYÊN TẮC QUAN TRỌNG NHẤT: Anti-hallucination

1. CHỈ dùng thông tin trong CONTEXT được Elasticsearch trả về
2. Không có CONTEXT phù hợp → "Tiếc quá bro, món này app chưa có nha"
3. KHÔNG bịa tên món, giá, rating, địa chỉ quán
4. KHÔNG trộn thông tin giữa các món khác nhau
5. Mỗi gợi ý PHẢI có: Tên món (bold), quán, shopId, giá VNĐ, rating (số đánh giá)

PHONG CÁCH:
- Thân thiện như bạn bè ("bro", "nha", "nè")
- Kể chuyện liền mạch, KHÔNG bullet point, KHÔNG số thứ tự
- Thêm cảm xúc ("ngon lắm bro", "rẻ bèo luôn")

SUY LUẬN ĐỊA ĐIỂM:
- "ở quận X có ... không?" → đọc trường Quận/Huyện trong CONTEXT
- Chỉ gợi ý món có quán TẠI quận/thành phố đó
- Không có → "Tiếc quá bro, app chưa có quán nào ở [địa điểm]..."

SUY LUẬN THÔNG MINH:
- Trời lạnh → "nước nóng", tags "súp", "lẩu"
- Healthy → tags "healthy", dinh dưỡng thấp calo
- Ăn khuya → tags "ăn khuya", preparation_time < 15 phút
- Rẻ → giá < 50k, Vừa → 50k-150k, Cao cấp → > 150k
- Top ngon → rating cao + nhiều đánh giá
```

---

## 7. ES SYNC — ĐỒNG BỘ DỮ LIỆU

### Auto sync (real-time)

```
Product thêm/sửa/xóa
    │ JPA @PostPersist / @PostUpdate / @PostRemove
    ▼
ProductEntityListener
    │ pushIndexEvent(productId) → Redis LIST
    ▼
Redis queue: es:chatbot:sync:queue
    │
    ▼ (poll mỗi 5 giây, batch 50 events)
EsChatbotSyncConsumer
    ├── Deduplicate theo productId
    ├── Fetch từ MySQL (JOIN shops để lấy địa chỉ)
    ├── Build context_text
    ├── gemini-embedding-001.embed(context_text) → 3072-dim vector
    └── Bulk index vào Elasticsearch (hoặc DELETE nếu bị xóa)
    
Latency: < 5 giây sau khi product thay đổi
```

### Manual full reindex (script Python)

```bash
# Khi nào cần chạy:
# - Deploy lần đầu (ES index trống)
# - Thay đổi buildContextText() (thêm field mới)
# - Đổi embedding model
# - Fix index corrupt

export GEMINI_API_KEY=your_key_here
python scripts/chatbot/sync_es_chatbot.py

# Script làm:
# 1. Xóa index cũ (tránh mapping conflict)
# 2. Tạo index mới: dense_vector 3072 dims + cosine similarity
# 3. Fetch tất cả products từ MySQL (JOIN shops)
# 4. Generate embedding qua Gemini API (gemini-embedding-001)
# 5. Bulk index vào Elasticsearch
```

---

## 8. API ENDPOINTS

```
Base: /api/user/chatbot  (yêu cầu JWT)

POST   /conversations                    → Tạo conversation mới
GET    /conversations                    → Danh sách conversations
PUT    /conversations/{id}              → Đổi tên conversation
DELETE /conversations/{id}              → Xóa conversation + messages
POST   /{conversationId}/messages       → Gửi tin nhắn, nhận reply AI
GET    /conversations/{id}/messages     → Lịch sử tin nhắn
```

### Response format gửi tin nhắn

```json
{
  "id": 42,
  "senderType": "BOT",
  "content": "Oi bro, nói đến món ngon Sài Gòn...",
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
      "imageUrl": "https://...",
      "navigationUrl": "product_detail/15"
    }
  ],
  "navigationUrls": ["product_detail/15", "product_detail/23"]
}
```

---

## 9. CẤU HÌNH

```properties
# Gemini API
gemini.api-key=${GEMINI_API_KEY}
gemini.model=gemini-2.5-flash
gemini.embedding-model=gemini-embedding-001

# Elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.index=foodtour_products_chatbot

# RAG
rag.max-results=6    # Số sản phẩm tối đa context cho LLM
rag.min-score=1.1    # Ngưỡng hybrid score
```

**Gemini Free Tier:**
| Giới hạn | Giá trị |
|---|---|
| Requests/phút | 15 |
| Requests/ngày | 1,500 |
| Tokens/phút | 1,000,000 |
| Chi phí | **Miễn phí** |

---

## 10. FILES LIÊN QUAN

```
service/
  ChatbotAI.java                     ← LangChain4j interface + system prompt
  ChatbotAIServiceImpl.java          ← Memory management + LLM orchestration
  ChatbotMessageServiceImpl.java     ← Message flow + product cards building
  FoodVectorServiceImpl.java         ← Hybrid search + embedding + ThreadLocal cache
  EsChatbotSyncConsumer.java         ← Background ES sync (Redis → ES, mỗi 5s)
  EsChatbotSyncProducerImpl.java     ← Push sync events to Redis

controller/UserController/
  ChatbotController.java             ← REST API endpoints

entity/
  ChatbotConversation.java
  ChatbotMessage.java

scripts/chatbot/
  sync_es_chatbot.py                 ← Full reindex script
```

---

## 11. CÁC CÂU HỎI GIÁO VIÊN CÓ THỂ HỎI

**Q: RAG là gì? Tại sao dùng RAG thay vì fine-tuning Gemini?**
> RAG (Retrieval-Augmented Generation): trước khi LLM trả lời, hệ thống tìm kiếm các tài liệu
> liên quan trong vector database, đưa vào context của LLM.
> Tại sao không fine-tune: dữ liệu thực phẩm thay đổi liên tục (giá, quán mới/đóng cửa).
> Fine-tuning = đóng băng kiến thức tại thời điểm train → không phản ánh dữ liệu mới.
> RAG = always up-to-date, chỉ cần sync ES khi có thay đổi.

**Q: Tại sao dùng hybrid search (BM25 + cosine) thay vì chỉ vector search?**
> Pure vector search yếu với exact match (tên sản phẩm cụ thể). Nếu user hỏi "Phở Thìn" —
> cosine similarity của "Phở Thìn" với một phở bò khác cao gần bằng → nhầm quán.
> BM25 exact match "Phở Thìn" rất chính xác. Hybrid = tốt nhất của cả hai:
> BM25 cho exact/keyword match, cosine cho semantic understanding.

**Q: Embedding 3072 chiều có lợi ích gì so với 768 chiều?**
> Nhiều chiều hơn → không gian vector phong phú hơn → phân biệt các khái niệm tương tự tốt hơn.
> gemini-embedding-001 (3072) hiểu ngữ nghĩa tiếng Việt tốt hơn các model nhỏ.
> Trade-off: storage lớn hơn (~12KB/document vs ~3KB), query latency cao hơn (~100ms vs ~30ms).

**Q: Tại sao cần ThreadLocal cache? Thread safety như thế nào?**
> Mỗi HTTP request được Spring xử lý bởi 1 thread → ThreadLocal là per-thread variable,
> không share giữa các requests → thread-safe tự nhiên.
> ContentRetriever (trong LangChain4j) set ThreadLocal, ChatbotMessageServiceImpl đọc và clear.
> Clear trong finally block đảm bảo không leak data sang request khác.

**Q: Caffeine cache vs Redis cache — tại sao chọn Caffeine cho conversation memory?**
> Caffeine: in-memory (RAM), latency ~microseconds, không network call.
> Redis: in-memory nhưng network call (~1ms), tốt cho shared state giữa nhiều instances.
> Conversation memory cần access rất nhanh với mỗi tin nhắn → Caffeine phù hợp hơn.
> Trade-off: khi restart server, memory mất → load lại từ MySQL.

**Q: Tại sao min-score = 1.1? Ảnh hưởng gì nếu thay đổi?**
> 1.1 = hybrid score tối thiểu (BM25×0.3 + cosine×0.7).
> Giảm xuống 0.8: nhiều kết quả hơn, nhưng LLM nhận context không liên quan → hallucinate.
> Tăng lên 1.5: ít kết quả hơn, chính xác hơn, nhưng câu hỏi hơi mơ hồ có thể trả về rỗng.
> 1.1 là giá trị thực nghiệm cân bằng recall và precision.

**Q: Background sync 5 giây có đủ nhanh không? Tại sao không sync ngay lập tức?**
> 5 giây đủ cho use case — người dùng hiếm khi hỏi chatbot về sản phẩm vừa tạo xong.
> Sync ngay lập tức: mỗi product save gọi Gemini Embedding API → thêm latency vào save operation.
> Batch 5 giây: deduplication (1 product sửa 10 lần chỉ sync 1 lần) → tiết kiệm API calls.

**Q: Gemini API rate limit 15 RPM có phải vấn đề không?**
> Với demo/development: không vấn đề gì.
> Production với nhiều user: cần billing plan ($0.10/1M tokens Gemini Flash — rất rẻ).
> Bottleneck thực tế: embedding generation khi full reindex (~2s/product × 1000 products = 30 phút).
> Giải pháp: batch embedding API, chạy reindex vào giờ thấp điểm.

**Q: Chatbot có thể bị prompt injection không?**
> Có nguy cơ nếu user cố tình viết instructions trong tin nhắn.
> Phòng ngừa: System prompt đặt instructions với quyền ưu tiên cao nhất, CONTEXT chỉ đọc.
> LLM hiện đại (Gemini 2.5) có built-in safety guardrails.
> Giải pháp nâng cao: sanitize input, rate limiting theo user, giới hạn độ dài tin nhắn.
