# 🤖 Tài Liệu Kiến Trúc Chatbot - FoodTour App

> Cập nhật: 10/03/2026

---

## 1. Tổng Quan Kiến Trúc

```
User gửi tin nhắn
        │
        ▼
[ChatbotController]
        │
        ▼
[ChatbotMessageService]  ──── lưu tin user vào MySQL
        │
        ▼
[ChatbotAIService]
        │
        ├── getOrCreateBot(conversationId)
        │         │
        │         ├── Nếu chưa có trong RAM (hoặc sau restart)
        │         │     → load 20 tin gần nhất từ MySQL → nạp vào ChatMemory
        │         └── Nếu đã có trong RAM + chưa expire (30 phút)
        │               → dùng luôn (có sẵn context)
        │
        ▼
[ChatbotAI (LangChain4j AiServices)]
        │
        ├── ChatLanguageModel (Ollama - qwen2.5:1.5b)   → sinh ra câu trả lời
        ├── ContentRetriever  (RAG - Elasticsearch)     → tìm sản phẩm liên quan
        └── ChatMemory        (MessageWindowChatMemory) → nhớ 20 tin gần nhất
        │
        ▼
[ChatbotMessageService]  ──── lưu tin bot vào MySQL
        │
        ▼
Trả về response cho User
```

---

## 2. Các Thành Phần Chi Tiết

### 2.1 ChatbotAI (LangChain4j Interface)
**File:** `service/ChatbotAI.java`

- Là interface LangChain4j, được implement tự động bởi `AiServices.builder()`
- Có `@SystemMessage` định nghĩa vai trò, phong cách, cách trả lời của bot
- Nhận `@UserMessage` là câu hỏi của user

**Vai trò của bot:**
- Tìm món ăn / quán ăn trong app
- Gợi ý theo tình huống (trời lạnh, ăn khuya, ăn cay...)
- Chỉ trả lời dựa trên CONTEXT từ database (không bịa)

---

### 2.2 ChatbotAIService - Memory Management
**File:** `service/impl/ChatbotAIServiceImpl.java`

```
Caffeine Cache (RAM):
├── conversationId=1 → ChatbotAI instance (memory 20 msgs) [expire sau 30 phút không dùng]
├── conversationId=2 → ChatbotAI instance (memory 20 msgs)
└── conversationId=3 → ChatbotAI instance (memory 20 msgs)
    tối đa 500 conversations cùng lúc
```

**Cơ chế `getOrCreateBot(conversationId)`:**

```
Lần đầu gọi (hoặc sau restart, hoặc sau 30 phút idle):
  1. Kiểm tra Caffeine Cache → chưa có
  2. Query MySQL: lấy 20 tin nhắn gần nhất của conversation đó
  3. Nạp từng tin vào ChatMemory (USER → UserMessage, BOT → AiMessage)
  4. Build ChatbotAI với memory đã có sẵn lịch sử
  5. Lưu vào Caffeine Cache

Lần sau gọi (trong vòng 30 phút):
  1. Kiểm tra Cache → đã có
  2. Trả về instance luôn (không query DB nữa)

Sau 30 phút không dùng:
  1. Caffeine tự xóa khỏi RAM
  2. Lần sau gọi lại → load từ DB như lần đầu (không mất data)
```

**Memory lifecycle:**
| Sự kiện | Memory RAM | Data DB |
|---------|-----------|---------|
| App khởi động | Trống | Còn nguyên |
| User gửi tin đầu tiên | Load từ DB vào Cache | Còn nguyên |
| Tiếp tục chat | Cập nhật trong Cache | Lưu mỗi tin |
| Không dùng 30 phút | Caffeine tự xóa | Còn nguyên ✅ |
| User xóa conversation | Xóa khỏi Cache (invalidate) | Xóa khỏi DB |
| App restart / tắt IDE | Cache mất | Còn nguyên ✅ |
| Bật lại app, chat tiếp | Load lại từ DB ✅ | Còn nguyên ✅ |

---

### 2.3 RAG - Retrieval Augmented Generation
**File:** `service/impl/FoodVectorServiceImpl.java`

**Luồng hoạt động khi user chat:**
```
User hỏi: "Có quán nào bán cơm rang không?"
        │
        ▼
1. Embed câu hỏi → vector 768 chiều (Ollama nomic-embed-text)
   [KHÔNG sync gì cả ở bước này]
        │
        ▼
2. Tìm kiếm trong Elasticsearch bằng cosine similarity
        │
        ▼
3. Trả về top N sản phẩm liên quan nhất (cấu hình rag.max-results)
        │
        ▼
4. Inject vào prompt dưới dạng CONTEXT
        │
        ▼
5. AI sinh câu trả lời dựa trên CONTEXT đó
```

**Format dữ liệu lưu vào Elasticsearch:**
```
"Món: Cơm Rang Dương Châu. Quán: Quán Cơm Ngon.
Mô tả: Cơm rang thơm ngon. Nguyên liệu: cơm, trứng, rau.
Tags: cơm, rang. Giá: 45000. Rating: 4.2 (150 đánh giá)."
```

---

### 2.4 Sync Sản Phẩm vào Elasticsearch

#### Sơ đồ toàn bộ sync strategy:

```
                    ┌─────────────────────────────────┐
                    │         App khởi động            │
                    │         @PostConstruct           │
                    └────────────────┬────────────────┘
                                     │ background thread
                                     ▼
                          ES có data không?
                         /                \
                       CÓ                KHÔNG
                        │                  │
                   SKIP hoàn toàn    syncAllProducts()
                   (restart bao      (chỉ chạy 1 lần
                   nhiêu cũng OK)     lần đầu setup)
```

```
        Shop thêm món / sửa món / xóa món
                        │
                        ▼ (JPA Entity Listener)
              @PostPersist / @PostUpdate
                        │
                        ▼
               syncProduct(product)       ← chỉ sync 1 sản phẩm đó
                        │
                        ▼
              @PreRemove → deleteProduct(id)  ← chỉ xóa 1 doc trong ES
```

#### Giải thích từng method:

| Method | Khi nào chạy | Làm gì |
|--------|-------------|--------|
| `checkAndSyncIfNeeded()` | App startup (background) | Check ES count → sync nếu trống |
| `syncAllProducts()` | Chỉ khi ES trống lần đầu | Embed + bulk upsert toàn bộ |
| `syncProduct(product)` | Mỗi khi thêm/sửa 1 sản phẩm | Embed + upsert đúng 1 doc |
| `deleteProduct(id)` | Mỗi khi xóa 1 sản phẩm | Xóa đúng 1 doc khỏi ES |

> ⚠️ **QUAN TRỌNG:** `getContentRetriever()` (được gọi mỗi request chatbot) **KHÔNG sync gì cả** — chỉ search ES thuần túy. Sync hoàn toàn tách biệt khỏi luồng chat.

> ⚠️ **Upsert không phải replace:** `syncProduct` dùng `id = product_id` → nếu đã tồn tại thì update, chưa có thì insert. Không xóa đi làm lại.

---

### 2.5 Nơi Lưu Trữ Dữ Liệu

```
D:\Project\BackEnd\
├── FoodTourApp_data\        ← MySQL data (tin nhắn, conversations, users...)
├── ElasticSearch_data\      ← Vector embeddings của sản phẩm (persistent trên disk)
└── Ollama_data\             ← AI models
    ├── nomic-embed-text      (~274MB) - embedding model
    └── qwen2.5:1.5b          (~1GB)   - chat model
```

| Loại dữ liệu | Nơi lưu | Mất khi restart app? | Mất khi tắt Docker? |
|-------------|---------|---------------------|---------------------|
| Tin nhắn chat | MySQL (disk) | ❌ Không | ❌ Không |
| Conversations | MySQL (disk) | ❌ Không | ❌ Không |
| Vector sản phẩm | Elasticsearch (disk) | ❌ Không | ❌ Không |
| AI Models | Ollama_data (disk) | ❌ Không | ❌ Không |
| Memory AI (context) | Caffeine Cache (RAM) | ✅ Mất → tự load lại từ DB | ✅ Mất → tự load lại từ DB |

---

## 3. Hành Vi Theo Từng Tình Huống

| Tình huống | Sync ES | Memory AI |
|-----------|---------|-----------|
| **Lần đầu setup** (ES trống) | ✅ Sync toàn bộ (background) | Trống → load từ DB khi chat |
| **Restart app / IDE** | ❌ SKIP (ES đã có data) | Load lại từ DB khi chat tiếp |
| **Restart Docker** | ❌ SKIP (data còn trên disk) | Load lại từ DB khi chat tiếp |
| **Shop thêm món mới** | ✅ Sync 1 sản phẩm (event) | Không ảnh hưởng |
| **Shop sửa món** | ✅ Update 1 sản phẩm (event) | Không ảnh hưởng |
| **Shop xóa món** | ✅ Xóa 1 doc ES (event) | Không ảnh hưởng |
| **User gửi tin chatbot** | ❌ Không sync gì | Dùng Cache hoặc load từ DB |
| **Xóa folder ElasticSearch_data** | ✅ Sync lại toàn bộ (cố ý) | Không ảnh hưởng |

---

## 4. Đánh Giá Tối Ưu

### 4.1 Những gì đã tốt ✅

| Điểm tốt | Giải thích |
|----------|-----------|
| Memory độc lập per conversation | Mỗi conversation có `ChatbotAI` riêng, không bị lẫn lộn |
| Caffeine Cache với TTL 30 phút | Tự dọn memory sau 30 phút idle, max 500 conversations |
| Load lịch sử từ DB sau restart | AI nhớ lại context dù app restart bao nhiêu lần |
| Sync không block request chatbot | `getContentRetriever()` chỉ search, không sync |
| Sync không block startup | `checkAndSyncIfNeeded()` chạy background thread |
| Restart app không sync lại | Check ES count trực tiếp, không dùng AtomicBoolean |
| Event-driven sync sản phẩm | Thêm/sửa/xóa → tự động sync đúng 1 sản phẩm |
| Upsert theo product_id | Không bị trùng dữ liệu trong Elasticsearch |
| Xóa memory khi xóa conversation | Không bị memory leak |
| Thread-safe | Caffeine Cache thread-safe |

---

### 4.2 Hạn Chế Còn Lại ⚠️

#### ❶ Ollama chạy local - chậm và nặng máy
```
Vấn đề:
- qwen2.5:1.5b: nhỏ nhưng chất lượng trả lời giới hạn
- Không có GPU → CPU inference → chậm (5-30s/request)
- nomic-embed-text embedding: cũng chạy local → mỗi lần sync 1 sản phẩm cũng tốn thời gian

Giải pháp thực tế (production):
- Dùng Gemini API / OpenAI API thay Ollama
- Embed: text-embedding-004 (Google) hoặc text-embedding-3-small (OpenAI)
- Chat: gemini-1.5-flash (free tier, nhanh) hoặc gpt-4o-mini
→ Không cần Docker Ollama, không tốn RAM, nhanh hơn 10-50x
```

#### ❷ syncAllProducts() khi ES trống - vẫn chậm nếu nhiều sản phẩm
```
Vấn đề: 100.000 sản phẩm × embed từng cái (Ollama local) = rất lâu
         Nhưng chỉ xảy ra 1 lần duy nhất khi lần đầu setup hoặc xóa ES data

Giải pháp: Dùng API cloud embedding → nhanh hơn rất nhiều
           Hoặc chấp nhận chờ lần đầu (vì chỉ xảy ra 1 lần)
```

#### ❸ MessageWindowChatMemory giới hạn 20 tin
```
Vấn đề: Chat nhiều hơn 20 tin → AI quên tin cũ

Đây là trade-off bình thường:
- Tăng lên 50 → tốn token context hơn → AI chậm hơn
- 20 là hợp lý cho chat thông thường về ăn uống
```

---

### 4.3 Sát Thực Tế Không?

| Tiêu chí | Setup hiện tại | Production thực tế |
|----------|---------------|-------------------|
| Chat model | Ollama local (qwen2.5:1.5b) | Gemini/OpenAI API |
| Embedding | Ollama local (nomic-embed-text) | Google/OpenAI Embedding API |
| Vector DB | Elasticsearch local (Docker) | Elastic Cloud / Pinecone / Weaviate |
| Memory Cache | Caffeine (RAM) | Caffeine hoặc Redis |
| Chat history | MySQL | Giống vậy ✅ |
| Memory pattern | Caffeine TTL + load từ DB | Giống vậy ✅ |
| RAG pattern | ES cosine similarity | Giống vậy ✅ |
| Sync strategy | Event-driven + check ES count | Giống vậy ✅ |
| Sync on restart | ❌ SKIP nếu ES có data ✅ | Giống vậy ✅ |
| Sync on request | ❌ Không sync ✅ | Giống vậy ✅ |

> **Kết luận:** Kiến trúc và strategy **đúng hướng production**. Điểm yếu duy nhất là dùng Ollama local thay vì API cloud — phù hợp để học và demo, khi deploy thật chỉ cần thay model provider.

---

## 5. Cải Tiến Nên Làm Khi Deploy Thật

### Thay Ollama → Gemini API (ưu tiên cao nhất)

```xml
<!-- pom.xml -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-google-ai-gemini</artifactId>
    <version>0.36.2</version>
</dependency>
```

```java
// Thay OllamaChatModel
ChatLanguageModel chatModel = GoogleAiGeminiChatModel.builder()
        .apiKey(System.getenv("GEMINI_API_KEY"))
        .modelName("gemini-1.5-flash")  // free tier, nhanh
        .build();

// Thay OllamaEmbeddingModel
EmbeddingModel embeddingModel = GoogleAiEmbeddingModel.builder()
        .apiKey(System.getenv("GEMINI_API_KEY"))
        .modelName("text-embedding-004")
        .build();
```

**Lợi ích:**
- Không cần Docker Ollama → tiết kiệm ~1.5GB RAM
- Nhanh hơn 10-50x
- Chất lượng trả lời tốt hơn nhiều
- Free tier Gemini đủ dùng cho demo/dev

---

## 6. Flow Đầy Đủ Khi User Chat

```
1. User POST /api/user/chatbot/{conversationId}/messages
   { "content": "trời lạnh ăn gì ngon?" }

2. ChatbotMessageService.sendMessage()
   ├── Kiểm tra conversation thuộc về user không
   ├── Lưu tin USER vào MySQL
   └── Gọi chatbotAIService.generateReply(conversationId, content)

3. ChatbotAIService.generateReply()
   └── getOrCreateBot(conversationId)
       ├── Caffeine Cache có → dùng luôn
       └── Caffeine Cache không có:
           ├── Query MySQL lấy 20 tin gần nhất
           ├── Nạp vào MessageWindowChatMemory
           └── Build ChatbotAI instance mới

4. ChatbotAI.chat("trời lạnh ăn gì ngon?")
   ├── ContentRetriever.retrieve("trời lạnh ăn gì ngon?")
   │   ├── Embed câu hỏi → vector (Ollama nomic-embed-text)
   │   ├── Search Elasticsearch cosine similarity  [KHÔNG SYNC]
   │   └── Trả về top N sản phẩm liên quan nhất
   ├── Inject CONTEXT vào prompt
   ├── Gửi [SystemMessage + ChatHistory + CONTEXT + UserMessage] → Ollama qwen2.5
   └── Nhận câu trả lời

5. ChatbotMessageService
   ├── Lưu tin BOT vào MySQL
   └── Trả về response cho user
```

---

## 7. Cấu Hình (application.properties)

```properties
# Ollama
ollama.base-url=http://localhost:11434
ollama.chat-model=qwen2.5:1.5b
ollama.embedding-model=nomic-embed-text

# Elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.index=food_products

# RAG
rag.max-results=5      # Số sản phẩm tìm được từ ES để inject vào prompt
rag.min-score=0.7      # Cosine similarity tối thiểu (0-1), dưới ngưỡng này bỏ qua
```
