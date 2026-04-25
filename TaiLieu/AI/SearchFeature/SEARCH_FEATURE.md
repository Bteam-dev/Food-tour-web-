# FoodTourApp - Hybrid Search Feature

## Tong quan

Tinh nang search YouTube-like voi:
- **Hybrid Search**: BM25 full-text + PhoBERT semantic (hieu tieng Viet, sai chinh ta, khong dau)
- **Trending**: hien khi guest bam vao thanh search
- **Search History**: hien khi user da dang nhap (xoa duoc)
- **Smart Suggestions**: goi y thong minh khi dang go (ket hop trending + products + history)

---

## Kien truc

```
+------------------+       +-------------------+       +-------------------+
|   Frontend       |       |  Spring Boot API  |       |  Elasticsearch    |
|  (Mobile/Web)    | ----> |                   | ----> |                   |
|                  |       |  Hybrid Search    |       |  search index     |
|  Search Bar      |       |  Suggestion Svc   |       |  suggestion index |
+------------------+       +-------------------+       +-------------------+
                                    |
                                    | HTTP (query embedding)
                                    v
                           +-------------------+
                           |  Google Colab      |
                           |  PhoBERT Server   |
                           |  (Cloudflare)     |
                           +-------------------+
```

### Components

| Component | Muc dich | Vi tri |
|-----------|----------|--------|
| `ProductSearchServiceImpl` | Hybrid search (BM25 + PhoBERT cosine) | `service/impl/` |
| `SearchSuggestionServiceImpl` | Trending, history, smart suggestions | `service/impl/` |
| `PublicSearchController` | API trending + suggestions (guest) | `controller/PublicController/` |
| `UserSearchController` | API history + suggestions (logged-in) | `controller/UserController/` |
| `SearchHistory` entity | Luu lich su search cua user | `entity/` |
| `SearchAnalytics` entity | Luu moi luot search (cho training) | `entity/` |
| `sync_es_search.py` | Index products + PhoBERT embeddings | `scripts/search/` |
| `sync_es_suggestions.py` | Index trending/suggestions + embeddings | `scripts/search/` |
| `SearchRecommend.md` | Colab notebook train PhoBERT | `resources/CollabTrain/` |

### Elasticsearch Indexes

| Index | Muc dich | Embedding |
|-------|----------|-----------|
| `foodtour_products_search` | Product listing, hybrid search | PhoBERT 768-dim |
| `foodtour_search_suggestions` | Trending, autocomplete, semantic suggest | PhoBERT 768-dim |

---

## API Endpoints

### Public (khong can dang nhap)

#### GET `/api/public/search/trending?limit=10`

Lay trending searches. Hien khi user chua dang nhap bam vao thanh search.

**Response:**
```json
{
  "success": true,
  "data": [
    { "queryText": "pho bo", "searchCount": 150, "isTrending": true },
    { "queryText": "banh mi", "searchCount": 120, "isTrending": true },
    { "queryText": "bun cha", "searchCount": 80, "isTrending": false }
  ]
}
```

#### GET `/api/public/search/suggest?q=pho&limit=8`

Smart suggestions khi dang go. Hybrid: PhoBERT + autocomplete + trending.

**Vi du:**
- `q=pho` → `["pho bo", "pho ga", "pho cuon"]`
- `q=poh` → `["pho bo", "pho ga"]` (PhoBERT hieu semantic)
- `q=bun` → `["bun bo", "bun cha", "bun rieu"]`

**Response:**
```json
{
  "success": true,
  "data": [
    { "text": "pho bo", "type": "trending", "searchCount": 150 },
    { "text": "pho ga", "type": "trending", "searchCount": 90 },
    { "text": "Pho Bo Tai Nam", "type": "product" }
  ]
}
```

`type` gom:
- `trending`: tu trending searches
- `history`: tu search history cua user (chi khi dang nhap)
- `product`: tu product name autocomplete
- `suggestion`: tu suggestion index (khong trending)

#### POST `/api/public/search/log-click`

Log khi guest click product tu ket qua search.

**Body:**
```json
{
  "queryText": "pho bo",
  "productId": 123,
  "clickPosition": 2,
  "sessionId": "abc-123"
}
```

#### GET `/api/public/products`

Main search + filter. Query params:

| Param | Loại | Mô tả |
|-------|------|-------|
| `keyword` | string | Từ khóa tìm kiếm (hybrid BM25 + PhoBERT) |
| `city` | string | Lọc theo thành phố (vd: "Hồ Chí Minh") |
| `district` | string | Lọc theo quận/huyện (vd: "Quận 1") — chỉ dùng khi đã chọn city |
| `categoryId` | int | Lọc theo danh mục |
| `sortBy` | string | `rating`, `price_asc`, `price_desc`, `newest`, `best_selling` |
| `minPrice` | long | Giá tối thiểu (VNĐ) |
| `maxPrice` | long | Giá tối đa (VNĐ) |
| `page` | int | Trang (default 0) |
| `size` | int | Số kết quả/trang (default 10) |

Khi PhoBERT online: BM25 full-text + PhoBERT cosine similarity (hiểu sai chính tả, không dấu).
Khi PhoBERT offline: fallback BM25 only (vẫn hoạt động bình thường).

#### GET `/api/public/shops/districts?city={city}`

Lấy danh sách quận/huyện có shop trong thành phố đã chọn.

```json
{
  "success": true,
  "data": ["Quận 1", "Quận 3", "Quận Bình Thạnh", "Tân Bình"]
}
```

Dùng để populate dropdown "Chọn quận" sau khi user chọn thành phố.

#### GET `/api/public/products/suggest?q=ph&limit=5`

Product autocomplete (giữ nguyên API cũ). Đã upgrade hybrid với PhoBERT.

---

### User (can dang nhap - JWT)

#### GET `/api/user/search/history?limit=10`

Lay search history. Hien khi user da dang nhap bam vao thanh search.

**Response:**
```json
{
  "success": true,
  "data": [
    { "id": 1, "queryText": "pho bo", "searchedAt": "2026-04-15T10:30:00" },
    { "id": 2, "queryText": "banh mi", "searchedAt": "2026-04-14T08:00:00" }
  ]
}
```

#### DELETE `/api/user/search/history/{id}`

Xoa 1 item (nut X ben canh).

#### DELETE `/api/user/search/history`

Xoa toan bo ("Xoa tat ca").

#### GET `/api/user/search/suggest?q=pho&limit=8`

Smart suggestions cho user da dang nhap. Giong public nhung them history matching (uu tien cao nhat).

#### POST `/api/user/search/log-click`

Log search click (giong public nhung co userId).

---

## Frontend Integration Guide

### Flow khi user tuong tac voi thanh search

```
1. User TAP vao thanh search
   |
   +-- Chua dang nhap?
   |     GET /api/public/search/trending
   |     → Hien danh sach trending
   |
   +-- Da dang nhap?
         GET /api/user/search/history
         → Hien danh sach history (co nut X de xoa)

2. User BAT DAU GO
   |
   +-- Chua dang nhap?
   |     GET /api/public/search/suggest?q={input}
   |
   +-- Da dang nhap?
         GET /api/user/search/suggest?q={input}
   |
   → Hien dropdown suggestions:
     - [clock icon] "pho bo"          (type=history, co nut X)
     - [fire icon]  "pho ga"          (type=trending)
     - [search icon] "Pho Bo Tai Nam" (type=product)

3. User NHAN SEARCH
   |
   GET /api/public/products?keyword={query}
   → Analytics duoc log tu dong (async)

4. User CLICK vao 1 product
   |
   POST /api/public/search/log-click
   Body: { queryText, productId, clickPosition }
   → Training data cho Colab
```

### Debounce

Suggest API nen duoc goi voi debounce 200-300ms de tranh spam.

---

## Hybrid Search - Chi tiet ky thuat

### Khi PhoBERT ONLINE (phobert.enabled=true)

```
User query: "poh"
     |
     v
+-- BM25 Match (weight: 0.3) --------+
|   MultiMatch trên:                   |
|   name^3, name_normalized^3,         |
|   description^1.5, tags^2, ...       |
|   fuzziness=AUTO, minShouldMatch=70% |
+--------------------------------------+
     |
     +-- PhoBERT Cosine (weight: 0.7) --+
     |   POST /embed {"text": "poh"}     |
     |   → embedding [0.1, -0.3, ...]    |
     |   cosineSimilarity(query, doc)     |
     +-----------------------------------+
     |
     v
Final Score = 0.3 * BM25 + 0.7 * (cosine + 1.0)
     |
     v
"pho bo" (score: 8.5)  ← PhoBERT hieu "poh" ~ "pho"
"pho ga" (score: 7.2)
"bun bo" (score: 2.1)  ← BM25 match "bo" nhung cosine thap
```

### Khi PhoBERT OFFLINE

```
User query: "poh"
     |
     v
BM25 only:
  MultiMatch + fuzziness=AUTO
  "poh" → fuzzy match "pho" (edit distance 1)
     |
     v
"pho bo" (score: 5.2)
"pho ga" (score: 4.8)
```

Van hoat dong, nhung khong hieu semantic tot bang hybrid.

### Config

```properties
# application.properties
phobert.server-url=https://xxx.trycloudflare.com
phobert.enabled=true
search.hybrid.bm25-weight=0.3
search.hybrid.semantic-weight=0.7
```

---

## Setup tu dau

### 1. Database

Spring Boot tu dong tao bang khi chay (ddl-auto=update):
- `search_histories`
- `search_analytics`

### 2. Train PhoBERT tren Colab

Xem `src/main/resources/CollabTrain/SearchRecommend.md`

**Tom tat:**
1. Upload `products.csv` + `search_analytics.csv`
2. Chay tat ca cells → download `embeddings.csv`, `suggestions.csv`
3. Chay Flask server + tunnel → lay URL

### 3. Index vao Elasticsearch

```bash
# Product search index (voi embeddings)
python scripts/search/sync_es_search.py --recreate-index --embeddings embeddings.csv

# Suggestion index (trending + embeddings)
python scripts/search/sync_es_suggestions.py --recreate-index --from-csv suggestions.csv
```

### 4. Config backend

```properties
phobert.server-url=https://xxx.trycloudflare.com
phobert.enabled=true
```

### 5. Restart Spring Boot

---

## Export data cho Colab

### products.csv

```sql
SELECT
    p.id, p.name, p.description, p.price, p.discount_price,
    p.image_urls, p.ingredients, p.tags,
    p.rating, p.total_reviews, p.is_available,
    s.shop_name, s.city as shop_city,
    c.name as category_name
FROM products p
LEFT JOIN shops s ON p.shop_id = s.id
LEFT JOIN categories c ON p.category_id = c.id
WHERE p.is_available = 1
INTO OUTFILE '/tmp/products.csv'
FIELDS TERMINATED BY ',' ENCLOSED BY '"'
LINES TERMINATED BY '\n';
```

### search_analytics.csv

```sql
SELECT
    id, user_id, query_text, query_normalized,
    result_count, clicked_product_id, click_position,
    session_id, searched_at
FROM search_analytics
ORDER BY searched_at DESC
INTO OUTFILE '/tmp/search_analytics.csv'
FIELDS TERMINATED BY ',' ENCLOSED BY '"'
LINES TERMINATED BY '\n';
```

---

## Khi Colab tat

- Search van hoat dong binh thuong (BM25 only)
- Trending van hoat dong (data da index trong ES)
- Suggestions van hoat dong (BM25 + autocomplete, khong co semantic)
- Search history van hoat dong (MySQL)
- Chi mat: semantic understanding (sai chinh ta nang, khong dau)

Khi can semantic lai: mo Colab → chay Cell 7 + 15 + 16 → copy tunnel URL.

---

## File structure

```
scripts/search/
  sync_es_search.py          # Index products (--embeddings embeddings.csv)
  sync_es_suggestions.py     # Index suggestions (--from-csv suggestions.csv)

src/main/java/.../
  entity/
    SearchHistory.java        # User search history (MySQL)
    SearchAnalytics.java      # All search logs (MySQL)
  repository/
    SearchHistoryRepository.java
    SearchAnalyticsRepository.java
  service/
    SearchSuggestionService.java        # Interface
    impl/
      SearchSuggestionServiceImpl.java  # Trending, history, smart suggest
      ProductSearchServiceImpl.java     # Hybrid search (updated)
  controller/
    PublicController/
      PublicSearchController.java       # Trending + suggest (guest)
      PublicProductController.java      # Main search + district filter
      PublicShopController.java         # GET /api/public/shops/districts?city=
    UserController/
      UserSearchController.java         # History + suggest (logged-in)

src/main/resources/
  CollabTrain/
    SearchRecommend.md                  # Colab notebook (train PhoBERT)
  application.properties                # PhoBERT config added
```
