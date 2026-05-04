# HƯỚNG DẪN SETUP REAL-TIME RECOMMENDATION SYSTEM

## Mục lục
1. [Tổng quan hệ thống](#1-tổng-quan-hệ-thống)
2. [Trạng thái các file model](#2-trạng-thái-các-file-model)
3. [Kiến trúc Lambda Architecture](#3-kiến-trúc-lambda-architecture)
4. [Kiến trúc Two-Tower Model](#4-kiến-trúc-two-tower-model)
5. [Auto-sync: Product & Shop changes](#5-auto-sync-product--shop-changes)
6. [Yêu cầu](#6-yêu-cầu)
7. [Bước 1: Chuẩn bị Database](#bước-1-chuẩn-bị-database)
8. [Bước 2: Export Training Data](#bước-2-export-training-data)
9. [Bước 3: Train Model trên Google Colab](#bước-3-train-model-trên-google-colab)
10. [Bước 4: Download và Deploy Model](#bước-4-download-và-deploy-model)
11. [Bước 5: Sync lên Elasticsearch](#bước-5-sync-lên-elasticsearch)
12. [Bước 6: Test APIs](#bước-6-test-apis)
13. [Cách hoạt động chi tiết](#7-cách-hoạt-động-chi-tiết)
14. [Scripts giải thích](#8-scripts-giải-thích)
15. [Troubleshooting](#9-troubleshooting)
16. [Re-train Model](#10-re-train-model)
17. [Checklist Setup](#11-checklist-setup)

---

## 1. Tổng quan hệ thống

### Tại sao cần train model?

Hệ thống gợi ý cần học từ dữ liệu để:
- Biết sản phẩm nào **ngữ nghĩa gần nhau** (phở ~ bún bò ~ hủ tiếu) trong không gian vector 64 chiều
- Học từ **hành vi mua hàng thực tế** — không chỉ dựa vào category/tags thủ công
- Precompute top-50 gợi ý cho từng user đã biết

### Flow tổng quan

```
DATABASE                GOOGLE COLAB              SPRING BOOT
────────                ────────────              ───────────

Users      ──┐
Products   ──┼──► Export JSON ──► Upload ──► Train Model ──► Download
Orders     ──┘    (Admin API)                      │
Behaviors                                          ▼
                                              model_output.zip
                                                   │
                                                   ▼
                              ┌──────────────────────────────────┐
                              │  Giải nén vào resources/         │
                              │  Chạy sync_es_rcm_behavior.py    │
                              │  Gọi hot-reload API (no restart) │
                              └──────────────────────────────────┘
```

### Sau khi setup xong — mọi thứ tự động

Khi product hoặc shop thay đổi trong DB, **không cần chạy script thủ công**:

```
Product create/update/delete
  → ProductEntityListener (JPA)
      ├─ es:chatbot:sync:queue    → EsChatbotSyncConsumer  (~5s) → foodtour_products_chatbot
      ├─ es:product:sync:queue   → EsProductSyncConsumer   (~2s) → foodtour_products_search
      └─ rec:recommend:sync:queue → EsRecommendSyncConsumer (~5s) → products_recommend

Shop update/delete
  → ShopEntityListener (JPA)
      └─ Re-queue tất cả products của shop đó → cả 3 index trên đều cập nhật
```

---

## 2. Trạng thái các file model

Sau khi train xong, `model_output/` có các file sau:

| File | Vai trò thực tế |
|------|----------------|
| `product_embeddings.json` | Backup local — Java chỉ dùng khi ES không available lúc startup |
| `es_documents.json` | Input cho `sync_es_rcm_behavior.py` — quan trọng nhất |
| `precomputed_recs.json` | Load vào Redis (`rec:model:batch:{userId}`) làm batch candidates |
| `config.json` | `embedding_dim=64` để sync script tạo đúng mapping ES |
| `two_tower_model.pt` | Dùng bởi `index_new_products.py` (optional, nâng cao chất lượng embedding) |
| `similar_products.json` | Không dùng — ES KNN thay thế |
| `user_encoder.pkl` | Không dùng trực tiếp — Java tính user embedding online |
| `product_encoder.pkl` | Không dùng trực tiếp — chỉ cần output đã export |

> **ES là nguồn chính:** Java đọc product embeddings từ ES `products_recommend` lúc startup,
> không đọc từ JSON. JSON chỉ là fallback khi ES không available.

---

## 3. Kiến trúc Lambda Architecture

```
BATCH LAYER                SPEED LAYER              SERVING LAYER
(Two-Tower — Colab)        (Real-time Java)          (Mỗi request)
────────────────           ──────────────            ──────────────

precomputed_recs      →    User behavior       →    Blend candidates
→ Redis (7d TTL)           tracking MySQL            80% realtime
                                 ↓                   20% batch
product embeddings        user_embedding                  ↓
→ ES products_recommend   → Redis (6h TTL)          Rerank bằng
  (serving store)                ↓                  realtime signal
→ RAM (startup load)       ES KNN search            (α=0.15 pos,
→ JSON (fallback)                                    β=0.85 boost)
                                                          ↓
                                                     Top-N results
```

### Tại sao blend 2 nguồn, tỷ lệ 80/20?

**Nếu chỉ dùng batch candidates (precomputed_recs):**
```
User lịch sử thích phở → precomputed_recs = [phở1..phở50]
User vừa click chè nhiều → rerank trong pool phở → VẪN RA PHỞ
(cosine_sim(chè_emb, phở_emb) ≈ 0, chè không bao giờ vào pool)
```

**Với blend 80/20:**
```
Realtime KNN (user_emb → ES): [chè1, chè2, cơm trộn1, ...]  ← 80% pool
Batch (precomputed):           [phở1, phở2, ...]             ← 20% pool (diversity)
Rerank (β=0.85 realtime):  chè/cơm trộn boost mạnh → nổi lên đầu
```

Batch candidates chỉ giữ để đảm bảo diversity — không để chúng "chèn" kết quả cũ vào.

### Luồng `getRecommendations()` chi tiết

```
① Loại sản phẩm tiêu cực (review xấu, remove cart)

② CANDIDATE GENERATION:
   Nguồn A (Realtime 80%): getUserEmbedding() → ES KNN → limit×3 candidates
   Nguồn B (Batch 20%):    getBatchCandidates() → Redis rec:model:batch:{userId}
                           → tối đa limit/2 candidates (chỉ để diversity)
   Blend: [realtime trước] + [batch cuối, capped] = pool

③ RERANK toàn pool bằng behaviors 72h gần nhất:
   position_score = 1 / (rank_in_pool + 1)
   realtime_boost = Σ cosine_sim(candidate, interacted) × 0.7^daysAgo × weight
   final_score    = 0.15 × position_score + 0.85 × realtime_boost

④ FALLBACK nếu pool < limit:
   Session-based → Category-based → Shop-based → Cold-start trending

⑤ Diversity (max 2/shop) → return top-N
```

---

## 4. Kiến trúc Two-Tower Model

Code train: `TaiLieu/AI/RecommendByBehavior/RealTimeRecommendation_Train.md`

### User Tower

```python
UserTower:
  user_embedding: Embedding(n_users, 64)
  mlp: Linear(64→128) → LayerNorm → ReLU → Dropout(0.2)
     → Linear(128→128) → LayerNorm → ReLU → Dropout(0.2)
     → Linear(128→64)
  output: L2 normalize → unit vector 64-dim
```

### Product Tower

```python
ProductTower:
  product_id_embedding: Embedding(n_products, 64)        # học từ interaction data
  text_projection: Linear(384→128) → LayerNorm → ReLU → Dropout(0.2) → Linear(128→64)
  fusion: Linear(128→128) → LayerNorm → ReLU → Dropout(0.2) → Linear(128→64)

  forward(product_idx, text_embedding):
    id_emb  = product_id_embedding(product_idx)            # 64-dim
    txt_emb = text_projection(text_embedding)              # 384 → 64-dim
    combined = concat([id_emb, txt_emb])                   # 128-dim
    output   = L2_normalize(fusion(combined))              # 64-dim unit vector
```

Text features:
```
text = name + description + ingredients[] + tags[]
embedding = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')  # 384-dim
```

### Tại sao Java không dùng User Tower trực tiếp?

User Tower output cần `user_idx` — chạy qua Embedding layer + MLP, không thể update real-time
khi user tương tác mà không gọi Python service. Java thay bằng:

```
user_embedding = Σ(0.7^daysAgo × weight × product_emb) / Σ(...)  → L2 normalize
```

Weighted average của product embeddings đã tương tác, có time decay. Trade-off có chủ đích:
**real-time responsiveness > độ chính xác tuyệt đối của model**.

### Embedding cho sản phẩm mới — progression tự động

Khi sản phẩm mới thêm vào DB, hệ thống tự động xử lý theo thứ tự:

| Giai đoạn | Embedding | Cách lấy | Độ trễ |
|-----------|---------|-----------|--------|
| **Ngay sau tạo product** | Category average | `EsRecommendSyncConsumer` tự động | ~5 giây |
| Sau `index_new_products.py` (optional) | Text-only Two-Tower | Script thủ công | Tùy |
| Sau retrain Colab + `sync_es_rcm_behavior.py` | Full Two-Tower | Định kỳ | Vài tuần |

Product mới **xuất hiện trong KNN ngay lập tức** mà không cần script nào.
`index_new_products.py` chỉ là bước nâng cao chất lượng embedding, không bắt buộc.

---

## 5. Auto-sync: Product & Shop changes

### ProductEntityListener

Tất cả thay đổi của Product entity (JPA `@PostPersist`, `@PostUpdate`, `@PostRemove`) tự động
trigger sync vào cả 3 ES indexes qua Redis queue:

```
Product.save() / delete()
  → ProductEntityListener
      ├─ EsChatbotSyncProducer  → es:chatbot:sync:queue
      ├─ EsProductSyncProducer  → es:product:sync:queue
      └─ EsRecommendSyncProducer → rec:recommend:sync:queue
```

### ShopEntityListener

Khi shop thay đổi (`@PostUpdate`, `@PostRemove`), toàn bộ products của shop đó được re-queue:

```
Shop.save() / delete()
  → ShopEntityListener
      └─ productRepository.findByShop(shop)
          → push tất cả product IDs → cả 3 Redis queues
```

Đảm bảo dữ liệu shop trong ES (`shop_name`, `shop_is_verified`, `shop_is_active`,
`shop_address`, ...) luôn cập nhật khi admin approve/reject/update shop.

### EsRecommendSyncConsumer — logic index sản phẩm mới

Consumer poll Redis mỗi 5 giây. Với mỗi event INDEX:
1. Fetch product từ DB
2. Lấy `categoryAverageEmbeddings.get(categoryId)` từ `RealTimeRecommendationService`
3. Upsert document vào `products_recommend` ES với `embedding_source: "category_average"`
4. Cập nhật in-memory maps (`productEmbeddings`, `productCategoryMap`, `productShopMap`) ngay lập tức

Với event DELETE:
1. Xóa khỏi ES `products_recommend`
2. Xóa khỏi in-memory maps

### Redis queues

| Queue | Consumer | Polling | ES index |
|-------|----------|---------|----------|
| `es:chatbot:sync:queue` | `EsChatbotSyncConsumer` | 5s | `foodtour_products_chatbot` |
| `es:product:sync:queue` | `EsProductSyncConsumer` | 2s | `foodtour_products_search` |
| `rec:recommend:sync:queue` | `EsRecommendSyncConsumer` | 5s | `products_recommend` |

---

## 6. Yêu cầu

Services cần chạy:
- MySQL
- Redis
- Elasticsearch
- Spring Boot app

Tools (chỉ cần cho lần setup đầu và retrain):
- Python 3.8+ với `pip install requests sentence-transformers torch`
- Google account (Colab)

---

## Bước 1: Chuẩn bị Database

### 1.1. Tạo bảng `user_behaviors`

```sql
CREATE TABLE IF NOT EXISTS user_behaviors (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    weight DOUBLE DEFAULT 1.0,
    session_id VARCHAR(100),
    source VARCHAR(50),
    view_duration_seconds INT,
    quantity INT,
    rating INT,
    category_id INT,
    shop_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_created_at (created_at),
    INDEX idx_session (session_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (product_id) REFERENCES products(id)
);
```

### 1.2. Kiểm tra dữ liệu

```sql
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM products;
SELECT COUNT(*) FROM order_items;
SELECT COUNT(*) FROM user_behaviors;
```

Cần có ít nhất: 50 users, 30 products, 100 interactions.

---

## Bước 2: Export Training Data

```bash
# Login admin
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@example.com", "password": "your_password"}'

# Download training data
curl http://localhost:8080/api/admin/recommendation/training-data/download \
  -H "Authorization: Bearer {admin_token}" \
  -o training_data.json
```

---

## Bước 3: Train Model trên Google Colab

1. Mở Google Colab → tạo notebook mới
2. Copy toàn bộ code từ `TaiLieu/AI/RecommendByBehavior/RealTimeRecommendation_Train.md`
3. Paste vào cell → chạy (Ctrl+Enter)
4. Khi thấy "Upload file training_data.json..." → chọn file vừa download
5. Đợi training xong (~5-15 phút) → Colab tự download `model_output.zip`

---

## Bước 4: Download và Deploy Model

Giải nén `model_output.zip` vào:
```
FoodTourApp/src/main/resources/models/FoodRecommendSearchByBehavior/model_output/
```

Cấu trúc sau khi giải nén:
```
model_output/
├── config.json               ← embedding_dim=64
├── es_documents.json         ← input cho sync script  [QUAN TRỌNG NHẤT]
├── product_embeddings.json   ← fallback khi ES down
├── precomputed_recs.json     ← batch candidates → Redis
├── two_tower_model.pt        ← dùng bởi index_new_products.py (optional)
├── similar_products.json     ← không dùng
├── user_encoder.pkl          ← không dùng trực tiếp
└── product_encoder.pkl       ← không dùng trực tiếp
```

---

## Bước 5: Sync lên Elasticsearch

```bash
cd FoodTourApp/scripts/recommend
python sync_es_rcm_behavior.py
```

Script sẽ:
1. Đọc `config.json` → lấy `embedding_dim=64`
2. **Xóa** index cũ `products_recommend`
3. Tạo index mới với mapping `dense_vector(dims=64, similarity=cosine)`
4. Bulk index tất cả products từ `es_documents.json` (có embedding đầy đủ từ Two-Tower)
5. Verify KNN search

Output mong đợi:
```
Documents to index: 483
✅ Index created successfully
Progress: 483/483
✅ Indexing complete! Success: 483, Failed: 0
✅ KNN search returned 5 results
```

---

## Bước 6: Test APIs

### Hot-reload (không cần restart app)

```bash
curl -X POST http://localhost:8080/api/admin/recommendation/reload-embeddings \
  -H "Authorization: Bearer {admin_token}"
```

Logs khi thành công:
```
✅ Loaded 483 product embeddings from ES (dim=64)
✅ Built category average embeddings for N categories
✅ Loaded precomputed recs for 72 users into Redis (TTL=7d)
```

> Nếu là lần đầu setup hoặc thay đổi code thì vẫn cần restart app.

### Get Recommendations

```bash
curl http://localhost:8080/api/user/recommendations/for-you?limit=10 \
  -H "Authorization: Bearer {user_token}"
```

### Track Behavior

```bash
curl -X POST http://localhost:8080/api/user/recommendations/track \
  -H "Authorization: Bearer {user_token}" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 5,
    "actionType": "VIEW",
    "sessionId": "session123",
    "source": "HOME",
    "viewDurationSeconds": 45
  }'
```

`actionType`: `VIEW`, `CLICK`, `SEARCH_CLICK`, `RECOMMEND_CLICK`, `ADD_CART`, `REMOVE_CART`, `WISHLIST_ADD`, `PURCHASE`, `REVIEW_POSITIVE`, `REVIEW_NEGATIVE`

`source`: `HOME`, `SEARCH`, `RECOMMENDATION`, `CATEGORY`, `SIMILAR`, `DIRECT`

---

## 7. Cách hoạt động chi tiết

### Startup Flow

```
@PostConstruct init():
  ① DB query → productShopMap, productCategoryMap (phải trước để dùng được cho category averages)
  ② ES products_recommend → Map<productId, float[64]> trong RAM  ← PRIMARY
     └─ Nếu ES down → fallback sang product_embeddings.json
  ③ Tính category average embeddings từ Map trên (dùng cho sản phẩm mới auto-sync)
  ④ precomputed_recs.json → Redis rec:model:batch:{userId} (TTL 7 ngày)

@Scheduled mỗi 2 tiếng:
  Reload lại embeddings từ ES + rebuild category averages
  → Pick up embeddings mới (sau retrain) mà không cần restart
```

### Real-time Tracking Flow

```
User click/view/mua hàng
  → POST /api/user/recommendations/track
  → trackBehavior() → buffer UserBehavior vào Redis List (non-blocking, ~1ms)
  → [ASYNC] updateUserEmbeddingAsync():
       Query behaviors 14 ngày gần nhất (từ MySQL, max lag 30s)
       user_emb[i] = Σ(0.7^daysAgo × weight × product_emb[i]) / Σ(decay × weight)
       product_emb: lấy từ RAM (ES đã load) hoặc category average nếu không có
       L2 normalize → unit vector 64-dim
       → Redis: rec:user:embedding:{userId} (TTL 6h)

  [BACKGROUND - mỗi 30 giây]
  → UserBehaviorBufferService.flush():
       LRANGE Redis buffer → batch saveAll → MySQL
       (max lag 30s, không ảnh hưởng embedding vì TTL 6h)

  [BACKGROUND - 3 giờ sáng]
  → Retention cleanup: xóa behaviors cũ hơn 90 ngày từ MySQL
    (90 ngày đủ cho training 6 tháng trong RecommendationDataExportService)
```

### SearchAnalytics - Elasticsearch thay MySQL

```
User search
  → logSearch() → ghi SearchAnalytics vào ES index "search_analytics" (async)
  → [KHÔNG ghi MySQL nữa - tránh table phình triệu records/ngày]

Trending queries
  → ES terms aggregation trên "query_normalized" field (nhanh hơn MySQL GROUP BY)

Retention: @Scheduled 3 giờ sáng → delete_by_query (xóa docs > 90 ngày)
```

### Lấy embedding cho sản phẩm trong Java

```
getProductEmbedding(productId):
  1. productEmbeddings.get(productId)           ← Two-Tower embedding từ ES (chính xác nhất)
  2. categoryAverageEmbeddings.get(categoryId)  ← trung bình category (fallback sản phẩm mới)
  3. null                                        ← bỏ qua (không có thông tin gì)
```

### Redis Keys

| Key | Nội dung | TTL | Cập nhật khi nào |
|-----|---------|-----|-----------------|
| `rec:model:batch:{userId}` | `List<productId>` top-50 từ Two-Tower | 7 ngày | Sau retrain + hot-reload |
| `rec:user:embedding:{userId}` | `float[64]` user embedding | 6 giờ | Sau mỗi behavior mới |
| `rec:recommend:sync:queue` | Queue product IDs cần sync recommend | Không có TTL | ProductEntityListener / ShopEntityListener |

### Weights theo Action Type

| Action | Weight | Ghi chú |
|--------|--------|---------|
| VIEW | 1.0 | Xem sản phẩm |
| VIEW > 30s | 1.5 | Xem kỹ |
| VIEW > 60s | 2.0 | Xem rất kỹ |
| CLICK | 1.5 | Click thông thường |
| SEARCH_CLICK | 2.0 | Click từ search |
| RECOMMEND_CLICK | 2.5 | Click từ gợi ý |
| WISHLIST_ADD | 2.0 | Thêm wishlist |
| ADD_CART | 3.0 | Thêm vào giỏ |
| PURCHASE | 5.0 | Mua hàng (×quantity, tối đa 3) |
| REVIEW_POSITIVE | 4.0 | Review 4-5 sao |
| REMOVE_CART | -1.0 | Xóa khỏi giỏ |
| REVIEW_NEGATIVE | -2.0 | Review 1-2 sao |

### Cold-start (user mới chưa có behavior)

| Tier | Nguồn | Mô tả |
|------|-------|-------|
| 1 | Trending | Sản phẩm được mua nhiều nhất trong 7 ngày qua |
| 2 | Diverse top-rated | 1 sản phẩm rating cao nhất từ mỗi danh mục |
| 3 | Top-rated | Fill nếu 2 tầng trên chưa đủ |

---

## 8. Scripts giải thích

Có 2 scripts liên quan đến ES recommend, mục đích hoàn toàn khác nhau.

### `sync_es_rcm_behavior.py` — chạy sau khi retrain

**Khi nào chạy:** Sau khi download `model_output.zip` từ Colab và giải nén xong.

**Làm gì:**
- Xóa toàn bộ index `products_recommend`
- Tạo lại từ đầu với data từ `es_documents.json`
- Mỗi sản phẩm trong file này có embedding **đầy đủ từ Two-Tower** (id_emb + text + fusion)

**Không cần chạy `index_new_products.py` trước hay sau.** `sync_es_rcm_behavior.py` là full refresh,
nó thay thế mọi thứ. Nếu retrain có bao gồm sản phẩm mới trong training data thì sản phẩm đó
sẽ có embedding thật sau bước này.

```bash
python scripts/recommend/sync_es_rcm_behavior.py
```

---

### `index_new_products.py` — nâng cao chất lượng embedding (không bắt buộc)

> **Từ khi có `EsRecommendSyncConsumer`, script này không còn bắt buộc nữa.**
> Sản phẩm mới tự động được index vào `products_recommend` với category-average embedding (~5 giây).
> Script này chỉ cần thiết khi muốn **nâng cao chất lượng** embedding lên mức text-only Two-Tower
> mà không cần retrain full.

**Vấn đề nó giải quyết:**

| Nguồn embedding | Chất lượng | Cách lấy |
|----------------|-----------|----------|
| Full Two-Tower | ★★★ (id_emb + text + fusion) | Sau retrain + `sync_es_rcm_behavior.py` |
| Text-only Two-Tower | ★★☆ (chỉ text_projection, thiếu id_emb) | `index_new_products.py` |
| Category average | ★☆☆ (trung bình hình học theo danh mục) | Tự động qua `EsRecommendSyncConsumer` |

**Làm gì:**
- Kiểm tra sản phẩm nào đang có trong ES `products_recommend`
- Lấy danh sách sản phẩm mới chưa có embedding (hoặc có embedding_source = "category_average")
- Load `two_tower_model.pt` → extract phần `text_projection` của Product Tower
- Chạy: `SentenceTransformer(text) → text_projection → L2_normalize` → 64-dim
- Upsert vào ES (không xóa index cũ, ghi đè lên category_average nếu có)

**Chất lượng embedding so với full Two-Tower:**
```
Full Two-Tower:  L2_normalize(fusion(concat(id_emb, text_proj(ST(text)))))
Text-only:       L2_normalize(text_proj(ST(text)))  ← thiếu id_emb (chưa được train)
```
Thiếu collaborative filtering signal (`id_emb`), nhưng vẫn dùng đúng weight `text_projection`
đã được train — tốt hơn category average vì sát với model thật hơn.

Embedding có field `"embedding_source": "text_only"` để phân biệt.

```bash
cd scripts/recommend
pip install sentence-transformers torch  # lần đầu

# Cách 1: qua API
python index_new_products.py --api-token <JWT admin token>

# Cách 2: qua file JSON
python index_new_products.py --products-file products.json
```

Sau đó gọi hot-reload để Java load embeddings mới từ ES:
```bash
curl -X POST http://localhost:8080/api/admin/recommendation/reload-embeddings \
  -H "Authorization: Bearer {admin_token}"
```

---

### Tóm tắt: tình huống và hành động cần làm

| Tình huống | Hành động |
|-----------|-----------|
| Product create/update/delete | **Tự động** — không cần làm gì |
| Shop approve/update/deactivate | **Tự động** — không cần làm gì |
| Vừa retrain xong trên Colab | `sync_es_rcm_behavior.py` → hot-reload API |
| Muốn nâng embedding từ category_avg → text-only (không retrain) | `index_new_products.py` → hot-reload API |
| ES bị xóa/corrupt | `sync_es_rcm_behavior.py` → hot-reload API |

---

## 9. Troubleshooting

### Recommendations không thay đổi sau khi tương tác

1. Kiểm tra behavior đã lưu chưa:
```sql
SELECT action_type, weight, created_at FROM user_behaviors
WHERE user_id = ? ORDER BY created_at DESC LIMIT 10;
```

2. Kiểm tra user embedding trong Redis:
```bash
redis-cli EXISTS "rec:user:embedding:{userId}"
# 1 = có → kiểm tra giá trị
# 0 = chưa có → gọi invalidate rồi tương tác lại
```

3. Force recalculate embedding:
```bash
curl -X POST http://localhost:8080/api/user/recommendations/invalidate-cache \
  -H "Authorization: Bearer {user_token}"
```

4. Đọc logs — pool size nói lên vấn đề gì:
```
[Rec] Pool: realtime=30, batch=5, blended=35 for user 3   ← bình thường
[Rec] Pool: realtime=0,  batch=5, blended=5  for user 3   ← user embedding null → không có embedding
[Rec] Pool: realtime=30, batch=0, blended=30 for user 3   ← user mới chưa có precomputed recs
```

5. Nếu `realtime=0`: kiểm tra recommend queue và ES coverage:
```bash
# Còn event nào trong queue chưa xử lý không?
redis-cli LLEN rec:recommend:sync:queue

# Bao nhiêu sản phẩm có embedding trong ES?
curl http://localhost:9200/products_recommend/_count

# Sản phẩm user vừa click có trong ES không?
curl -X POST http://localhost:9200/products_recommend/_search \
  -H "Content-Type: application/json" \
  -d '{"query": {"term": {"product_id": <productId>}}}'
```

Nếu product không có trong ES sau 10 giây → kiểm tra logs của `EsRecommendSyncConsumer`.

### ES KNN trả về 0 kết quả

`embedding_dim` không khớp:
1. Kiểm tra `config.json` → `embedding_dim` phải = 64
2. Chạy lại `sync_es_rcm_behavior.py`

### Startup log báo load từ JSON thay vì ES

```
⚠️ ES unavailable during embedding load, falling back to JSON
```
Elasticsearch chưa khởi động xong. Đợi ES healthy rồi gọi hot-reload:
```bash
curl -X POST http://localhost:8080/api/admin/recommendation/reload-embeddings \
  -H "Authorization: Bearer {admin_token}"
```

### Shop approve xong nhưng ES vẫn hiện shop chưa verified

`ShopEntityListener` đã xử lý tự động, nhưng consumer cần ~5 giây để xử lý. Nếu sau 10 giây vẫn chưa cập nhật:
```bash
# Kiểm tra queue
redis-cli LLEN es:chatbot:sync:queue
redis-cli LLEN es:product:sync:queue
redis-cli LLEN rec:recommend:sync:queue
```

---

## 10. Re-train Model

### Khi nào nên retrain?

- Sau 2-4 tuần tích lũy behavior data đáng kể
- Chất lượng recommend giảm rõ rệt
- Muốn nâng sản phẩm mới từ category-average lên full Two-Tower embedding

> Recommendations thay đổi **real-time theo behavior** mà KHÔNG cần retrain.
> Retrain chỉ để cải thiện chất lượng nền (product embeddings tốt hơn, precomputed recs sát hơn).

### Steps

```
1. Export data mới:
   GET /api/admin/recommendation/training-data/download
   Authorization: Bearer {admin_token}

2. Train lại trên Colab:
   Paste code từ RealTimeRecommendation_Train.md
   Upload training_data.json mới → chạy → download model_output.zip

3. Giải nén đè lên folder cũ:
   src/main/resources/models/FoodRecommendSearchByBehavior/model_output/

4. Sync Elasticsearch:
   python scripts/recommend/sync_es_rcm_behavior.py

5. Hot-reload (KHÔNG cần restart):
   POST /api/admin/recommendation/reload-embeddings
```

Hot-reload tự động:
- Reload embeddings từ ES vào RAM
- Rebuild category average embeddings
- Reload `precomputed_recs.json` vào Redis (TTL reset 7 ngày)
- User embeddings tự expire sau 6h hoặc ghi đè khi có behavior mới

---

## 11. Checklist Setup

**Lần đầu setup:**
- [ ] MySQL có bảng `user_behaviors`
- [ ] Có ít nhất 50 users, 30 products, 100 interactions
- [ ] Export được `training_data.json`
- [ ] Train thành công trên Colab → download `model_output.zip`
- [ ] Giải nén vào `resources/models/FoodRecommendSearchByBehavior/model_output/`
- [ ] Elasticsearch đang chạy, Redis đang chạy
- [ ] Chạy `sync_es_rcm_behavior.py` thành công
- [ ] Gọi hot-reload API hoặc restart Spring Boot
- [ ] Startup log: `✅ Loaded N product embeddings from ES (dim=64)`
- [ ] `GET /api/user/recommendations/for-you?limit=10` trả về data
- [ ] Track vài behavior → gọi lại `/for-you` → kết quả thay đổi
- [ ] User mới (chưa có behavior) nhận được cold-start recommendations

**Kiểm tra auto-sync hoạt động:**
- [ ] Tạo product mới → sau ~5 giây → xuất hiện trong `products_recommend` ES
- [ ] Admin approve shop → sau ~5 giây → `shop_is_verified=true` trong ES
- [ ] Xóa product → sau ~5 giây → không còn trong ES
