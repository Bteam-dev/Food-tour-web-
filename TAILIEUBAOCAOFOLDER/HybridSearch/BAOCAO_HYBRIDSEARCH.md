# TÀI LIỆU BÁO CÁO: HYBRID SEARCH
> BM25 Full-text + PhoBERT Semantic 768-dim | Elasticsearch + Spring Boot
> Hiểu tiếng Việt, sai chính tả, không dấu | Trending + Search History + Smart Suggestions

---

## 1. TỔNG QUAN

### Mục tiêu
Tính năng tìm kiếm thông minh kiểu YouTube:
- **Hybrid Search**: BM25 full-text + PhoBERT semantic — hiểu "poh" = "phở", "bun bo" = "bún bò"
- **Trending**: hiển thị khi user click vào thanh search
- **Search History**: lịch sử cá nhân (logged-in), có thể xóa
- **Smart Suggestions**: gợi ý realtime khi đang gõ — trending + products + history + semantic

### Tech Stack

| Component | Technology | Vai trò |
|---|---|---|
| Search Engine | Elasticsearch 8.x | Lưu products + dense_vector 768-dim |
| Semantic Model | PhoBERT (768-dim) | Hiểu ngữ nghĩa tiếng Việt |
| Serving | Google Colab + Cloudflare tunnel | Chạy PhoBERT Flask server |
| Full-text | BM25 (Elasticsearch built-in) | Exact/fuzzy keyword match |
| Cache | Redis | Cache embedding 30 phút |
| Analytics | Elasticsearch (search_analytics) | Log mọi lượt search (không MySQL) |
| History | MySQL (search_histories) | Lưu lịch sử user |

---

## 2. KIẾN TRÚC VÀ PIPELINE

### 2.1 Hai chế độ hoạt động

```
CHẾ ĐỘ 1: BM25 Only (phobert.enabled=false)
  → Full-text + fuzzy match
  → Hoạt động ngay khi Colab tắt
  → Không hiểu semantic ("poh" chỉ match fuzzy "pho")

CHẾ ĐỘ 2: Hybrid (phobert.enabled=true)
  → BM25 (30%) + PhoBERT cosine (70%)
  → Cần Colab chạy PhoBERT Flask server
  → Hiểu "poh", "bun bo", "cam xuc dong" v.v.
```

### 2.2 Hybrid Search Pipeline

```
User gõ query: "poh"
    │
    ▼
ProductSearchServiceImpl.search()
    │
    ├── 1. Chuẩn hóa query (normalize, lowercase, bỏ dấu nếu cần)
    │
    ├── 2. BM25 query → Elasticsearch
    │       MultiMatch: name^3, name_normalized^3, description^1.5, tags^2,
    │                   shop_name^1, category_name^2, shop_city^1.5
    │       fuzziness = AUTO (edit distance 1-2)
    │       minShouldMatch = 70%
    │
    ├── 3. Check PhoBERT enabled?
    │       YES: check Redis cache "phobert:emb:poh" (TTL 30 phút)
    │              Cache HIT → dùng cached vector
    │              Cache MISS → POST PhoBERT server /embed → vector[768]
    │                         → save Redis cache
    │
    ├── 4. Build Elasticsearch hybrid query:
    │       {
    │         "query": { "bool": {
    │           "should": [
    │             { "multi_match": { "query": "poh", "boost": 0.3 } },  ← BM25 30%
    │             { "script_score": {
    │                 "query": { "match_all": {} },
    │                 "script": {
    │                   "source": "0.7 * (cosineSimilarity(params.vector, 'embedding') + 1.0)",
    │                   "params": { "vector": [0.1, -0.3, ...] }  ← PhoBERT 70%
    │                 }
    │             }}
    │           ]
    │         }}
    │       }
    │
    └── 5. Sort by score, filter is_available=true, paginate
    
    ▼
Final Score = 0.3 × BM25_score + 0.7 × (cosine + 1.0)
"pho bo" (score: 8.5) ← PhoBERT hiểu "poh" ~ "phở"
"pho ga" (score: 7.2)
"bun bo" (score: 2.1) ← BM25 match "bo" nhưng cosine thấp
```

**Tại sao cộng 1.0 vào cosine?**
Cosine ∈ [-1, 1]. Cộng 1.0 → [0, 2]. Tránh giá trị âm khi blend với BM25 dương.

### 2.3 Trending & Suggestions Pipeline

```
User click vào thanh search (chưa gõ)
    │
    ├── Chưa đăng nhập → GET /api/public/search/trending?limit=10
    │       → SearchAnalyticsEsServiceImpl.getTrending()
    │       → ES terms aggregation trên "query_normalized" field
    │       → Top 10 queries theo searchCount trong 30 ngày
    │
    └── Đã đăng nhập → GET /api/user/search/history?limit=10
            → SearchHistoryRepository.findByUserId()
            → MySQL query (sorted by searchedAt DESC)

User bắt đầu gõ "ph..."
    │
    ▼
GET /api/public/search/suggest?q=ph&limit=8
    │
    ├── Trending prefix match: "phở bò", "phở gà", "phở cuốn"
    ├── Product autocomplete: "Phở Thìn", "Phở Bình"
    ├── PhoBERT semantic: "poh" → gợi ý tương tự (nếu enabled)
    └── (Logged-in): History match: "phở đặc biệt" (user đã search trước đó)
    
Response: [
  { "text": "phở bò", "type": "trending", "searchCount": 150 },
  { "text": "Phở Thìn", "type": "product" },
  { "text": "phở đặc biệt", "type": "history" }  ← chỉ khi logged-in
]
```

---

## 3. PHOBERT — TRAIN VÀ DEPLOY

### 3.1 PhoBERT là gì?

- PhoBERT = Vietnamese BERT — pretrained trên 20GB tiếng Việt từ Wikipedia + news
- 768-dim sentence embeddings (dùng `paraphrase-multilingual-MiniLM-L12-v2` hoặc VnCoreNLP)
- Hiểu ngữ nghĩa: "bun bo" ~ "bún bò", "mi tom" ~ "mì tôm", "com ga" ~ "cơm gà"

### 3.2 Colab — Setup PhoBERT Server

```python
# Cell 1: Install
!pip install sentence-transformers flask flask-cors cloudflare pyngrok

# Cell 2: Load model
from sentence_transformers import SentenceTransformer
model = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')
# Model 384MB, tải ~2-3 phút lần đầu

# Cell 3: Flask server
from flask import Flask, request, jsonify
app = Flask(__name__)

@app.route('/embed', methods=['POST'])
def embed():
    text = request.json.get('text', '')
    vector = model.encode(text).tolist()  # 768-dim
    return jsonify({'embedding': vector, 'dims': len(vector)})

# Cell 4: Cloudflare tunnel (free, không cần đăng ký)
# cloudflared tunnel → random URL như https://xxx.trycloudflare.com
!curl -fsSL https://github.com/cloudflare/cloudflared/releases/.../cloudflared-linux-amd64 -o cloudflared
!chmod +x cloudflared
# Chạy server + tunnel
import threading
threading.Thread(target=lambda: app.run(port=5000)).start()
!./cloudflared tunnel --url http://localhost:5000
# → Copy URL ra: phobert.server-url=https://xxx.trycloudflare.com
```

### 3.3 Export Data và Index Elasticsearch

**Bước 1: Export products.csv từ MySQL (chạy local)**
```python
# export_products.py
# JOIN products + shops + categories
# Convert JSON arrays → pipe-separated (tags, ingredients)
# Output: products.csv với ~500+ sản phẩm
```

**Bước 2: Export search_analytics.csv**
```bash
# SearchAnalytics đã chuyển sang Elasticsearch (không còn MySQL)
curl -H "Authorization: Bearer <ADMIN_TOKEN>" \
     "http://localhost:8080/api/admin/search/analytics/export?days=90" \
     -o search_analytics.csv
# Cần ít nhất 100 rows có clicked_product_id để train có ý nghĩa
```

**Bước 3 — Colab cells (SearchRecommend.md)**
```
Cell 1-5: Setup + Upload products.csv + search_analytics.csv
Cell 6-8: PhoBERT encode tất cả product names + descriptions → 768-dim vectors
Cell 9-11: Tính search relevance scores từ analytics (click-through rate)
Cell 12-14: Build suggestion index với embeddings
Cell 15: Generate embeddings.csv + suggestions.csv
Cell 16: Chạy Flask server + Cloudflare tunnel
```

**Bước 4: Index vào Elasticsearch**
```bash
# Product search index
python scripts/search/sync_es_search.py --recreate-index --embeddings embeddings.csv

# Suggestion index
python scripts/search/sync_es_suggestions.py --recreate-index --from-csv suggestions.csv
```

### 3.4 Cập nhật config

```properties
phobert.server-url=https://xxx.trycloudflare.com
phobert.enabled=true
search.hybrid.bm25-weight=0.3
search.hybrid.semantic-weight=0.7
```

---

## 4. ELASTICSEARCH INDEXES

### Index 1: `foodtour_products_search`

| Field | Type | Mô tả |
|---|---|---|
| `id` | integer | Product ID |
| `name` | text | Tên món (BM25, boost x3) |
| `name_normalized` | text | Tên không dấu (fuzzy) |
| `description` | text | Mô tả (boost x1.5) |
| `tags` | keyword | Tags (boost x2) |
| `shop_name` | text | Tên quán |
| `category_name` | text | Danh mục (boost x2) |
| `shop_city` | keyword | Thành phố (filter) |
| `shop_district` | keyword | Quận/Huyện (filter) |
| `price` | scaled_float | Giá (filter range) |
| `rating` | double | Rating (sort) |
| `is_available` | boolean | Còn bán (filter) |
| `embedding` | dense_vector (768, cosine) | PhoBERT vector |

### Index 2: `foodtour_search_suggestions`

| Field | Type | Mô tả |
|---|---|---|
| `query_text` | text | Từ khóa gợi ý |
| `query_normalized` | keyword | Từ khóa chuẩn hóa |
| `search_count` | long | Số lần search |
| `is_trending` | boolean | Có phải trending không |
| `type` | keyword | trending/product/suggestion |
| `embedding` | dense_vector (768, cosine) | Vector cho semantic suggest |

### Index 3: `search_analytics` (Elasticsearch thay MySQL)

```
Tại sao dùng ES thay MySQL cho analytics?
→ MySQL: triệu records/ngày → table phình to, query GROUP BY chậm
→ ES: terms aggregation cực nhanh, built-in 90-day retention với delete_by_query
→ Không cần MySQL table, không cần index thêm

Fields: id, user_id, query_text, query_normalized, clicked_product_id,
        click_position, result_count, session_id, searched_at
```

---

## 5. SEARCH ANALYTICS FLOW

```
User gõ query "phở bò" → nhấn search
    │
    ▼
ProductSearchServiceImpl.search():
    ├── 1. Ghi SearchHistory vào MySQL (nếu đã đăng nhập, synchronous)
    └── 2. logSearch() gửi SearchAnalytics vào ES (async, non-blocking)

User click vào sản phẩm từ kết quả search
    │
    ▼
POST /api/public/search/log-click
    Body: { queryText, productId, clickPosition, sessionId }
    → Update SearchAnalytics trong ES (clicked_product_id, click_position)
    → Dữ liệu này dùng để retrain PhoBERT (click relevance signal)

Trending calculation (mỗi khi GET /trending)
    │
    ▼
ES aggregation:
    GET search_analytics/_search
    {
      "size": 0,
      "query": { "range": { "searched_at": { "gte": "now-30d" } } },
      "aggs": {
        "top_queries": {
          "terms": { "field": "query_normalized", "size": 10 }
        }
      }
    }
→ Top 10 queries có search_count cao nhất trong 30 ngày
```

---

## 6. AUTO-SYNC SẢN PHẨM MỚI

```
Product thêm/sửa/xóa
    │ JPA ProductEntityListener
    ▼
Redis queue: es:product:sync:queue
    │
    ▼ (poll mỗi 2 giây — nhanh nhất trong 3 indexes)
EsProductSyncConsumer
    ├── Fetch product từ MySQL
    ├── Build text for embedding: name + description + tags
    ├── POST PhoBERT server /embed → vector[768] (nếu enabled)
    │   Fallback: zero vector nếu PhoBERT offline
    └── Upsert vào foodtour_products_search
    
Latency: < 2 giây sau khi product thay đổi
```

---

## 7. API ENDPOINTS

### Public (không cần đăng nhập)

```
GET /api/public/search/trending?limit=10
    → Top trending searches trong 30 ngày

GET /api/public/search/suggest?q=pho&limit=8
    → Smart suggestions khi đang gõ
    → Response: [{ text, type: "trending|product|suggestion" }]

POST /api/public/search/log-click
    → { queryText, productId, clickPosition, sessionId }
    → Click tracking cho training data

GET /api/public/products?keyword=pho&city=HCM&minPrice=20000&sortBy=rating&page=0&size=10
    → Main search với đầy đủ filters

GET /api/public/shops/districts?city=Hồ Chí Minh
    → Danh sách quận có shop
```

### User (cần JWT)

```
GET /api/user/search/history?limit=10
    → Lịch sử search (MySQL, sorted by searchedAt DESC)

DELETE /api/user/search/history/{id}
    → Xóa 1 item

DELETE /api/user/search/history
    → Xóa tất cả

GET /api/user/search/suggest?q=pho&limit=8
    → Smart suggest + thêm history matching (ưu tiên cao nhất)
```

---

## 8. KHI COLAB TẮT — FALLBACK

```
phobert.enabled=false hoặc Colab timeout

Tác động:
✓ Search vẫn hoạt động (BM25 only — full-text + fuzzy)
✓ Trending vẫn hiện (data đã index trong ES)
✓ Suggestions vẫn hoạt động (autocomplete + trending)
✓ Search history vẫn hiển thị (MySQL)
✗ Mất: semantic understanding (sai chính tả nặng, không dấu hoàn toàn)

Khi cần semantic lại:
1. Mở Colab → chạy Cell 7 (load model) + Cell 15 + 16 (Flask server + tunnel)
2. Copy URL mới → cập nhật phobert.server-url
3. Restart Spring Boot (hoặc nếu có hot-reload config)
```

---

## 9. FILES LIÊN QUAN

```
service/impl/
  ProductSearchServiceImpl.java     ← Hybrid BM25 + PhoBERT cosine search
  SearchSuggestionServiceImpl.java  ← Trending, history, smart suggest
  SearchAnalyticsEsServiceImpl.java ← Log analytics vào ES + trending + export
  EsProductSyncConsumer.java        ← Background sync (poll 2s)

controller/
  PublicController/
    PublicSearchController.java     ← Trending + suggest (guest)
    PublicProductController.java    ← Main search + filters
    PublicShopController.java       ← GET districts
  UserController/
    UserSearchController.java       ← History + suggest (logged-in)
  AdminController/
    AdminSearchDataController.java  ← Export analytics CSV cho Colab

entity/
  SearchHistory.java               ← user_id, queryText, searchedAt (MySQL)
  
scripts/search/
  sync_es_search.py                ← Index products với PhoBERT embeddings
  sync_es_suggestions.py           ← Index suggestions

TaiLieu/AI/SearchFeature/
  SEARCH_FEATURE.md                ← Overview
  SearchRecommend.md               ← Colab notebook full
  fake_data_search.md              ← Demo data
```

---

## 10. CẤU HÌNH

```properties
phobert.server-url=https://xxx.trycloudflare.com
phobert.enabled=true
search.hybrid.bm25-weight=0.3
search.hybrid.semantic-weight=0.7
elasticsearch.index.search=foodtour_products_search
elasticsearch.index.suggestions=foodtour_search_suggestions
elasticsearch.index.analytics=search_analytics
```

---

## 11. CÁC CÂU HỎI GIÁO VIÊN CÓ THỂ HỎI

**Q: Hybrid search là gì? Tại sao không chỉ dùng một loại?**
> Hybrid = kết hợp BM25 full-text + vector cosine similarity.
> BM25 mạnh với exact match (tên quán, tên món cụ thể), yếu với semantic/typo.
> Vector search mạnh với semantic ("đồ ăn ngon" ~ "món ăn tuyệt"), yếu với exact (score phẳng).
> Hybrid 30/70 = best of both: BM25 đảm bảo exact match, vector bổ sung semantic.

**Q: PhoBERT vs BERT thông thường — tại sao chọn PhoBERT?**
> BERT gốc train trên tiếng Anh — không hiểu tiếng Việt tốt (tokenization sai, từ ghép bị tách).
> PhoBERT pretrained trên 20GB tiếng Việt — hiểu word segmentation, diacritics, slang.
> Quan trọng: "poh" ≈ "phở", "bun bo" ≈ "bún bò" nhờ PhoBERT — BERT thường không làm được.

**Q: Tại sao dùng Cloudflare tunnel thay vì deploy API riêng?**
> Colab không có IP public — cần tunnel để expose ra ngoài.
> Cloudflare tunnel: miễn phí, không cần đăng ký domain, URL ngẫu nhiên.
> Nhược điểm: URL thay đổi mỗi khi restart Colab → phải cập nhật config.
> Production: deploy lên GCP/AWS với fixed endpoint.

**Q: Redis cache embedding có cần thiết không?**
> Có. PhoBERT encode ~50-200ms/query. Không có cache: mỗi request gọi Colab = chậm.
> Cache Redis 30 phút: query "phở bò" lần 2 trả về ngay (~1ms).
> Cache key: "phobert:emb:{query_normalized}" — normalize trước khi cache (lowercase, bỏ dấu).

**Q: SearchAnalytics tại sao chuyển sang Elasticsearch thay vì MySQL?**
> MySQL: mỗi search ghi 1 row → triệu records/ngày → table phình → GROUP BY trending chậm.
> ES: built-in terms aggregation cực nhanh cho top queries. Delete_by_query tự động cleanup 90 ngày.
> Analytics là append-only, không cần ACID → ES phù hợp hơn.

**Q: Khi PhoBERT tắt, search có bị ảnh hưởng không?**
> Không — graceful degradation. `phobert.enabled=false` → BM25 only mode.
> BM25 với fuzziness=AUTO vẫn match được typo nhẹ (edit distance 1-2).
> Chỉ mất: semantic understanding — "bun bo" không match "bún bò" nếu không có PhoBERT.

**Q: Fuzzy search hoạt động thế nào? Giới hạn như thế nào?**
> fuzziness=AUTO: length 1-2 → no fuzzy, 3-5 → edit distance 1, 6+ → edit distance 2.
> Edit distance 1: "poh" → "pho" (thêm 1 ký tự = 1 phép biến đổi).
> Giới hạn: "poh" không match "phở" vì "ở" ≠ "o" trong edit distance. PhoBERT xử lý case này.

**Q: Tại sao weight BM25:Cosine = 30:70?**
> Thực nghiệm: user search tiếng Việt thường dùng ngôn ngữ tự nhiên, không exact keyword.
> 70% semantic → hiểu intent tốt hơn. 30% BM25 → vẫn ưu tiên exact match khi có.
> Có thể tune qua: search.hybrid.bm25-weight và search.hybrid.semantic-weight trong config.

**Q: Search history có được cá nhân hóa không?**
> History chỉ lưu query text của chính user đó → cá nhân hóa 100% (isolation by userId).
> Không share history giữa users. GDPR compliant: user xóa được (DELETE /history/{id}).
> Trending là aggregated, không cá nhân hóa — dựa trên tổng cộng toàn user base.

**Q: Edge ngram là gì? Có dùng không?**
> Edge ngram: "phở" → "p", "ph", "phở" — autocomplete nhanh hơn fuzzy search.
> Dùng trong `foodtour_search_suggestions` index để autocomplete tên sản phẩm.
> Khi user gõ "ph..." → edge ngram match "phở bò", "phở gà" mà không cần PhoBERT.
