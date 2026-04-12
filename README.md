# FoodTour Chatbot - AI Food Assistant

Chatbot tro ly mon an thong minh, su dung RAG (Retrieval-Augmented Generation) ket hop Google Gemini API + Elasticsearch de goi y mon an chinh xac tu du lieu thuc trong database.

---

## Kien truc tong quan

```
User gui tin nhan
    |
    v
ChatbotController (REST API)
    |
    v
ChatbotMessageServiceImpl
    |-- Luu tin nhan user vao DB
    |-- Goi ChatbotAIServiceImpl.generateReply()
    |       |
    |       |-- Load conversation memory (Caffeine cache)
    |       |-- LangChain4j AiServices goi:
    |       |       |
    |       |       |-- [1] ContentRetriever (RAG)
    |       |       |       |-- Embed query bang Google text-embedding-004
    |       |       |       |-- Hybrid search tren Elasticsearch:
    |       |       |       |     BM25 text match (30%) + cosine similarity (70%)
    |       |       |       |-- Loc theo min-score, tra ve top 6 san pham
    |       |       |       |-- Cache product IDs vao ThreadLocal
    |       |       |       v
    |       |       |-- [2] Google Gemini 2.0 Flash
    |       |       |       |-- System prompt + CONTEXT (san pham) + user message
    |       |       |       v
    |       |       |-- Tra ve cau tra loi tu nhien
    |       |
    |-- Luu tin nhan bot vao DB
    |-- Lay product IDs tu cache (KHONG goi ES lan 2)
    |-- Query DB lay thong tin san pham day du
    |-- Build product cards (ten, quan, gia, rating, anh)
    v
Response: { botReply, suggestedProducts[], navigationUrls[] }
```

---

## Tech Stack

| Component | Technology | Vai tro |
|-----------|-----------|---------|
| LLM (Chat) | Google Gemini 2.0 Flash | Sinh cau tra loi tu nhien bang tieng Viet |
| Embedding | Google text-embedding-004 | Chuyen text thanh vector 768 chieu |
| Vector Search | Elasticsearch 8.11.1 | Luu tru + tim kiem vector + full-text |
| RAG Framework | LangChain4j 0.36.2 | Ket noi LLM + retriever + memory |
| Conversation Memory | Caffeine Cache | Luu lich su hoi thoai trong RAM (30 phut) |
| Event Queue | Redis | Dong bo san pham vao ES theo thoi gian thuc |
| Database | MySQL 8.0 | Luu hoi thoai, tin nhan, san pham |
| Sync Script | Python + google-generativeai | Full reindex Elasticsearch |

---

## API Endpoints

Base URL: `/api/user/chatbot`

**Yeu cau xac thuc**: Tat ca endpoint can JWT token trong header `Authorization: Bearer <token>`

### Quan ly hoi thoai

| Method | Endpoint | Request Body | Response | Mo ta |
|--------|----------|-------------|----------|-------|
| POST | `/conversations` | `{ "title": "Hoi ve mon ngon" }` | `{ id, title, createdAt }` | Tao hoi thoai moi |
| GET | `/conversations` | - | `[{ id, title, createdAt, updatedAt }]` | Danh sach hoi thoai (moi nhat truoc) |
| PUT | `/conversations/{id}` | `{ "title": "Ten moi" }` | `{ id, title, ... }` | Doi ten hoi thoai |
| DELETE | `/conversations/{id}` | - | 200 OK | Xoa hoi thoai + toan bo tin nhan |

### Nhan tin

| Method | Endpoint | Request Body | Response | Mo ta |
|--------|----------|-------------|----------|-------|
| POST | `/{conversationId}/messages` | `{ "content": "Mon gi ngon o SG?" }` | Xem chi tiet ben duoi | Gui tin nhan, nhan tra loi AI |
| GET | `/conversations/{id}/messages` | - | `[{ id, senderType, content, createdAt }]` | Lich su tin nhan |

### Response format khi gui tin nhan

```json
{
  "id": 42,
  "senderType": "BOT",
  "senderName": "FoodTour Bot",
  "content": "Oi bro, noi den mon ngon Sai Gon thi phai thu **Banh Mi Thit Nuong** ...",
  "createdAt": "2026-04-11T10:30:00",
  "navigationUrls": [
    "product_detail/15",
    "product_detail/23"
  ],
  "suggestedProducts": [
    {
      "productId": 15,
      "productName": "Banh Mi Thit Nuong",
      "shopName": "Banh Mi Huynh Hoa",
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

| Field | Type | Mo ta |
|-------|------|-------|
| `content` | String | Cau tra loi tu nhien cua bot (Markdown, bold ten mon) |
| `navigationUrls` | String[] | Deep link toi trang chi tiet san pham |
| `suggestedProducts` | Object[] | Danh sach san pham goi y (sap xep theo do phu hop giam dan) |

---

## Hybrid Search (BM25 + Vector)

Chatbot su dung **hybrid search** ket hop 2 phuong phap de tim san pham chinh xac:

### Tai sao can hybrid?

| Phuong phap | Gioi o dau | Yeu o dau |
|-------------|-----------|-----------|
| **BM25** (text match) | Tim chinh xac tu khoa ("pho bo", "bun cha") | Khong hieu ngu nghia ("an gi troi lanh") |
| **Vector** (cosine similarity) | Hieu ngu nghia, y dinh | Co the miss ket qua khi hoi chinh xac ten mon |
| **Hybrid** (ket hop) | Ca hai | - |

### Cong thuc scoring

```
final_score = bm25_score * 0.3 + (cosine_similarity + 1.0) * 0.7
```

### Cac truong duoc search

| Field | Boost | Vi du |
|-------|-------|-------|
| `name` | x3 | "Pho Bo", "Banh Mi" |
| `category_name` | x2 | "Mon nuoc", "Do uong" |
| `description` | x1 | "Nuoc dung ninh xuong 12 tieng..." |
| `tags` | x1 | "cay", "healthy", "an khuya" |
| `ingredients` | x1 | "thit bo", "rau muong" |
| `shop_name` | x1 | "Pho Thin", "Bun Cha Huong Lien" |

### Config

```properties
# application.properties
rag.max-results=6          # Tra ve toi da 6 san pham
rag.min-score=1.1          # Nguong diem toi thieu (hybrid score)
```

---

## Elasticsearch Index

### Index name: `foodtour_products_chatbot`

### Mapping

| Field | Type | Mo ta |
|-------|------|-------|
| `id` | integer | Product ID |
| `name` | text | Ten mon an (BM25 searchable) |
| `description` | text | Mo ta mon an |
| `ingredients` | text | Nguyen lieu |
| `nutrition_info` | text | Thong tin dinh duong |
| `tags` | keyword | Tags ("cay", "healthy", "an khuya") |
| `shop_name` | text | Ten cua hang |
| `category_name` | text | Ten danh muc |
| `price` | scaled_float | Gia goc |
| `discount_price` | scaled_float | Gia khuyen mai |
| `preparation_time` | integer | Thoi gian chuan bi (phut) |
| `rating` | double | Diem danh gia trung binh |
| `total_reviews` | long | Tong so luot danh gia |
| `is_available` | boolean | Con ban khong |
| `context_text` | text | Noi dung co cau truc cho RAG |
| `embedding` | dense_vector (768 dims, cosine) | Vector embedding |

### Context text format (truyen vao LLM)

```
===PRODUCT===
Mon: Pho Bo Tai Chin
Quan: Pho Thin (shopId: 5)
Danh muc: Mon nuoc
Mo ta: Pho bo truyen thong Ha Noi, nuoc dung ninh xuong 12 tieng
Nguyen lieu: Banh pho, thit bo tai, thit bo chin, hanh la, rau mui
Tags: truyen thong, mon nuoc, sang
Dinh duong: 480 kcal, 25g protein, 45g carb
Thoi gian chuan bi: 10 phut
Gia: 55000 VND
Rating: 4.6/5.0 (215 danh gia)
===END_PRODUCT===
```

---

## Dong bo du lieu (ES Sync)

### Tu dong (Runtime)

```
San pham thay doi (them/sua/xoa)
    |
    v
EsChatbotSyncProducerImpl
    |-- Push event vao Redis queue: es:chatbot:sync:queue
    v
EsChatbotSyncConsumer (chay background, poll moi 5 giay)
    |-- Pop batch 50 events
    |-- Deduplicate theo productId
    |-- Fetch san pham tu DB
    |-- Generate embedding (Google text-embedding-004)
    |-- Build context_text
    |-- Bulk index vao Elasticsearch
```

### Thu cong (Full reindex)

Chay khi: deploy lan dau, doi embedding model, can rebuild toan bo index.

```bash
# Cai thu vien
pip install pymysql elasticsearch google-generativeai

# Set API key
export GEMINI_API_KEY=your_key_here

# Chay sync
python scripts/chatbot/sync_es_chatbot.py
```

Script se:
1. Tao index voi mapping neu chua co
2. Fetch toan bo san pham dang ban tu MySQL
3. Tao context_text cho tung san pham
4. Generate embedding 768 chieu qua Google API
5. Bulk index vao Elasticsearch

---

## System Prompt

Bot duoc cau hinh voi cac nguyen tac:

### Chong hallucination (QUAN TRONG NHAT)
- **CHI** dung thong tin trong CONTEXT duoc Elasticsearch tra ve
- Khong co CONTEXT → tra loi "Tiec qua bro, mon nay app chua co"
- KHONG bia ten mon, gia, rating, quan. KHONG tron thong tin giua cac mon
- Moi mon goi y BAT BUOC co: **Ten mon** (bold), quan (shopId), gia VND, rating (so danh gia)

### Phong cach tra loi
- Than thien, tu nhien nhu ban be ("bro", "nha", "ne")
- Ke chuyen lien mach, KHONG bullet point, KHONG so thu tu
- Them cam xuc ("ngon lam bro", "re beo luon")

### Suy luan thong minh
- Ten mon cu the → match truong "Mon:"
- Quan nao ban → lay shopId + ten quan
- Danh muc → dung "Danh muc:"
- Troi lanh/nong/khuya/healthy → doc mo ta + tags + nguyen lieu + dinh duong
- Gia: <100k = re, 100-200k = vua, >200k = cao cap
- Thoi gian: <15 phut = nhanh, 15-20 = vua, >20 = lau
- Top ngon: uu tien rating cao + nhieu danh gia

---

## Conversation Memory

| Config | Gia tri | Mo ta |
|--------|---------|-------|
| Cache type | Caffeine | In-memory, tu dong don dep |
| Max conversations | 500 | Toi da 500 hoi thoai dong thoi trong RAM |
| Expire | 30 phut | Khong hoat dong 30 phut → xoa khoi RAM |
| Message window | 20 tin nhan | LLM chi nhan 20 tin gan nhat lam context |
| Persistence | MySQL | Toan bo tin nhan luu DB, load lai khi can |

**Flow khoi tao memory:**
1. User gui tin nhan trong conversation
2. Kiem tra Caffeine cache co conversation khong
3. Neu KHONG co → load 20 tin nhan gan nhat tu DB → nap vao memory
4. Neu CO → dung memory san co
5. Sau 30 phut khong hoat dong → tu dong xoa khoi RAM

---

## Toi uu hieu suat

### 1. ThreadLocal cache (tranh double embedding)

**Van de:** Moi tin nhan goi Elasticsearch 2 lan:
- Lan 1: RAG ContentRetriever (lay context cho LLM)
- Lan 2: `retrieveProductIds()` (lay product IDs cho card UI)

**Giai phap:** ContentRetriever cache product IDs vao ThreadLocal → `retrieveProductIds()` doc tu cache.

```
Truoc: 2 lan embedding + 2 lan ES query = ~2s
Sau:   1 lan embedding + 1 lan ES query = ~1s (nhanh gap doi)
```

### 2. Caffeine cache cho conversation memory

Khong can load lich su tu DB moi lan gui tin nhan. Chi load 1 lan, sau do dung memory trong RAM.

### 3. Background sync (Redis event queue)

San pham thay doi → push event vao Redis → consumer xu ly batch 50 events moi 5 giay. Khong anh huong toi API response time.

---

## Cau hinh

### application.properties

```properties
# Google Gemini API (free: 15 RPM, 1500 req/ngay)
gemini.api-key=${GEMINI_API_KEY}
gemini.model=gemini-2.0-flash
gemini.embedding-model=text-embedding-004

# Elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.index=foodtour_products_chatbot

# RAG
rag.max-results=6       # So san pham toi da tra ve
rag.min-score=1.1        # Nguong diem hybrid search
```

### .env

```env
# Lay key tai: https://aistudio.google.com/apikey
GEMINI_API_KEY=your_gemini_api_key_here
```

### Free tier Google Gemini

| Gioi han | Gia tri |
|----------|---------|
| Requests/phut | 15 |
| Requests/ngay | 1,500 |
| Tokens/phut | 1,000,000 |
| Gia | **Mien phi** |

Du cho dev/demo. Production can billing (rat re: ~$0.10/1M tokens).

---

## Files lien quan

```
src/main/java/com/example/FoodTourApp/
├── config/
│   └── AiConfig.java                          # Bean GoogleAiGeminiChatModel
├── controller/UserController/
│   └── ChatbotController.java                 # REST API endpoints
├── DTO/ChatbotDTO/
│   ├── CreateChatbotConversationRequest.java   # Request tao hoi thoai
│   ├── SendChatbotMessageRequest.java          # Request gui tin nhan
│   ├── ChatbotConversationResponse.java        # Response hoi thoai
│   └── ChatbotMessageResponse.java             # Response tin nhan + product cards
├── entity/
│   ├── ChatbotConversation.java                # Entity hoi thoai
│   └── ChatbotMessage.java                     # Entity tin nhan
├── repository/
│   ├── ChatbotConversationRepository.java      # Query hoi thoai
│   └── ChatbotMessageRepository.java           # Query tin nhan
├── service/
│   ├── ChatbotAI.java                          # LangChain4j interface + system prompt
│   ├── ChatbotAIService.java                   # Interface AI service
│   ├── ChatbotMessageService.java              # Interface message service
│   ├── FoodVectorService.java                  # Interface vector/RAG service
│   ├── EsChatbotSyncProducer.java              # Interface sync producer
│   └── impl/
│       ├── ChatbotAIServiceImpl.java           # Memory management + LLM orchestration
│       ├── ChatbotMessageServiceImpl.java      # Message flow + product cards
│       ├── FoodVectorServiceImpl.java          # Hybrid search + embedding + cache
│       ├── EsChatbotSyncConsumer.java          # Background ES sync (Redis → ES)
│       └── EsChatbotSyncProducerImpl.java      # Push sync events to Redis

scripts/chatbot/
└── sync_es_chatbot.py                          # Python full reindex script
```

---

## Troubleshooting

| Van de | Nguyen nhan | Giai phap |
|--------|------------|-----------|
| Bot tra loi "app chua co du lieu" | ES index rong hoac embedding model khac | Chay lai `sync_es_chatbot.py` |
| Bot tra loi cham | Gemini API latency | Binh thuong ~1-3s, kiem tra mang |
| Loi 429 (rate limit) | Vuot 15 RPM free tier | Doi 1 phut hoac nang cap billing |
| Bot bja thong tin | min-score qua thap, CONTEXT khong lien quan | Tang `rag.min-score` |
| Tim khong ra mon | min-score qua cao | Giam `rag.min-score` |
| Embedding dimension mismatch | Doi model ma chua rebuild index | Xoa index, chay lai sync script |
| ES connection refused | Elasticsearch chua chay | `docker-compose up -d elasticsearch` |
