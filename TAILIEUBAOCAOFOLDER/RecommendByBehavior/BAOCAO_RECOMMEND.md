# TÀI LIỆU BÁO CÁO: RECOMMEND BY BEHAVIOR (GỢI Ý DỰA TRÊN HÀNH VI)
> Two-Tower Neural Network (64-dim) | Lambda Architecture: Batch + Realtime
> Google Colab Training + Spring Boot Serving | Elasticsearch KNN + Redis

---

## 1. TỔNG QUAN

### Mục tiêu
Hệ thống gợi ý sản phẩm cá nhân hóa theo hành vi người dùng:
- Học từ hành vi thực tế (xem, click, mua hàng, review...)
- Kết hợp Batch (Two-Tower model) + Realtime (hành vi gần đây)
- Tự động cập nhật khi có sản phẩm/shop thay đổi

### Tech Stack

| Component | Technology | Vai trò |
|---|---|---|
| Recommendation Model | Two-Tower Neural Network (64-dim) | Học user/product embeddings từ interaction |
| Text Embedding | SentenceTransformer MiniLM-L12 (384-dim) | Embed product text → input ProductTower |
| Vector DB | Elasticsearch (products_recommend) | KNN search 64-dim product embeddings |
| Batch Cache | Redis rec:model:batch:{userId} (TTL 7d) | Precomputed batch recommendations |
| User Embedding | Redis rec:user:embedding:{userId} (TTL 6h) | Realtime user embedding |
| Behavior Buffer | Redis list + MySQL (flush 30s) | Buffer hành vi realtime |
| Training | Google Colab (GPU) | Train Two-Tower model |

---

## 2. KIẾN TRÚC LAMBDA ARCHITECTURE

```
BATCH LAYER (Colab)         SPEED LAYER (Java)          SERVING LAYER (mỗi request)
────────────────────        ──────────────────          ─────────────────────────────

Two-Tower model train       User behavior tracking       Blend candidates:
  ↓                           ↓                            80% realtime KNN
precomputed_recs.json       MySQL user_behaviors            20% batch diversity
  ↓                           ↓
Redis rec:model:batch       user_embedding (64-dim)        Rerank:
  (7d TTL)                  weighted avg product embs       0.85 × realtime_boost
                             → Redis rec:user:embedding      0.15 × position_score
product embeddings (64-dim)   (6h TTL)
  ↓                                                         Diversity:
ES products_recommend                                        max 2 sp/shop
  (KNN 64-dim)
  ↓
RAM (startup load)                                         Top-N results
```

### Tại sao blend 80% realtime + 20% batch?

```
VẤN ĐỀ nếu chỉ batch:
  User lịch sử thích phở → precomputed = [phở1, phở2, ..., phở50]
  User vừa click chè 10 lần → Rerank trong pool phở → VẪN RA PHỞ!
  (cosine_sim(chè_embedding, phở_embedding) ≈ 0 → chè không bao giờ vào pool)

GIẢI PHÁP blend 80/20:
  Realtime KNN (user_emb → ES): [chè1, chè2, cơm trộn1] ← 80% pool (reflect current interest)
  Batch (precomputed):          [phở1, phở2]             ← 20% pool (diversity, long-term)
  Rerank (β=0.85 realtime): chè/cơm trộn boost mạnh → nổi lên đầu
```

---

## 3. KIẾN TRÚC TWO-TOWER MODEL

### 3.1 User Tower

```python
class UserTower(nn.Module):
    def __init__(self, n_users, embedding_dim=64):
        self.user_embedding = nn.Embedding(n_users, 64)  # học từ interaction data
        self.mlp = nn.Sequential(
            nn.Linear(64, 128),
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 128),
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 64)
        )
    
    def forward(self, user_idx):
        emb = self.user_embedding(user_idx)  # [B, 64]
        out = self.mlp(emb)                   # [B, 64]
        return F.normalize(out, dim=-1)       # L2 normalize → unit vector
```

### 3.2 Product Tower

```python
class ProductTower(nn.Module):
    def __init__(self, n_products, embedding_dim=64):
        self.product_id_embedding = nn.Embedding(n_products, 64)  # ID-based
        self.text_projection = nn.Sequential(
            nn.Linear(384, 128),   # 384 = SentenceTransformer MiniLM output
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 64)
        )
        self.fusion = nn.Sequential(
            nn.Linear(128, 128),   # concat [id_emb, txt_emb] = 128-dim
            nn.LayerNorm(128),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(128, 64)
        )
    
    def forward(self, product_idx, text_embedding):
        id_emb  = self.product_id_embedding(product_idx)  # [B, 64]
        txt_emb = self.text_projection(text_embedding)     # [B, 384] → [B, 64]
        combined = torch.cat([id_emb, txt_emb], dim=-1)   # [B, 128]
        out = self.fusion(combined)                         # [B, 64]
        return F.normalize(out, dim=-1)
```

**Text features cho ProductTower:**
```python
text = product.name + " " + product.description + " " + "|".join(ingredients) + " " + "|".join(tags)
text_embedding = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2').encode(text)
# → 384-dim multilingual embedding (hiểu tiếng Việt)
```

### 3.3 Training — Loss Function

```python
# Contrastive Loss (in-batch negative)
# Với mỗi (user, product) pair trong batch:
# Positive: product user đã tương tác (PURCHASE > ADD_CART > VIEW)
# Negative: products khác trong cùng batch (in-batch negatives)

def contrastive_loss(user_embs, product_embs, temperature=0.07):
    # Compute similarity matrix [B, B]
    scores = torch.matmul(user_embs, product_embs.T) / temperature
    
    # Diagonal = positive pairs, off-diagonal = negatives
    labels = torch.arange(scores.size(0))
    loss = F.cross_entropy(scores, labels)
    return loss

# Temperature 0.07: tập trung vào hard negatives
# Thấp hơn → phân biệt tốt hơn nhưng training unstable
# Cao hơn → training ổn hơn nhưng embeddings less discriminative
```

---

## 4. QUY TRÌNH TRAIN — COLAB

### Bước 1: Export Training Data từ Backend

```bash
# Login admin
curl -X POST http://localhost:8080/api/auth/login \
  -d '{"email": "admin@...", "password": "..."}'

# Download training data JSON
curl http://localhost:8080/api/admin/recommendation/training-data/download \
  -H "Authorization: Bearer {token}" \
  -o training_data.json
```

**Cấu trúc training_data.json:**
```json
{
  "users": [{"id": 1, "username": "user1"}, ...],
  "products": [{"id": 5, "name": "Phở Bò", "description": "...", "tags": [...], ...}],
  "interactions": [
    {"userId": 1, "productId": 5, "actionType": "PURCHASE", "weight": 5.0, "timestamp": "..."},
    {"userId": 1, "productId": 12, "actionType": "VIEW", "weight": 1.0, "timestamp": "..."}
  ]
}
```

**Action weights:**
| Action | Weight | Lý do |
|---|---|---|
| PURCHASE | 5.0 | Hành động mạnh nhất — user thực sự mua |
| REVIEW_POSITIVE | 4.0 | Đánh giá tích cực |
| ADD_CART | 3.0 | Có ý định mua |
| WISHLIST_ADD | 2.5 | Yêu thích |
| RECOMMEND_CLICK | 2.0 | Click từ gợi ý |
| SEARCH_CLICK | 1.5 | Click từ search |
| VIEW | 1.0 | Xem qua |
| REMOVE_CART | -1.0 | Tiêu cực nhẹ |
| REVIEW_NEGATIVE | -3.0 | Tiêu cực mạnh |

### Bước 2: Google Colab — Training cells

```python
# Cell 1: Upload training_data.json
from google.colab import files
uploaded = files.upload()  # chọn training_data.json

# Cell 2: Install + Import
!pip install sentence-transformers torch scikit-learn
from sentence_transformers import SentenceTransformer
import torch, json, numpy as np

# Cell 3: Load và Parse data
data = json.load(open('training_data.json'))
# Tạo user_id_map, product_id_map (0-indexed)
# Filter: chỉ dùng interactions có weight > 0

# Cell 4: Generate product text embeddings (SentenceTransformer)
text_model = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')
product_texts = [p['name'] + ' ' + p.get('description','') for p in products]
text_embeddings = text_model.encode(product_texts, batch_size=32, show_progress_bar=True)
# → [n_products, 384]

# Cell 5: Định nghĩa TwoTower model (xem kiến trúc ở trên)

# Cell 6: Train
optimizer = Adam(model.parameters(), lr=1e-3, weight_decay=1e-4)
scheduler = CosineAnnealingLR(optimizer, T_max=EPOCHS)
EPOCHS = 50  # early stopping patience=10

for epoch in range(EPOCHS):
    for batch in train_loader:
        user_idx, pos_idx, pos_text = batch
        user_emb = model.user_tower(user_idx)
        prod_emb = model.product_tower(pos_idx, pos_text)
        loss = contrastive_loss(user_emb, prod_emb)
        loss.backward()
        optimizer.step()
    
    # Monitor: Recall@10 trên val set
    # Lưu best model khi Recall@10 tốt nhất

# Cell 7: Export model outputs
# product_embeddings.json → {productId: embedding[64]}
# precomputed_recs.json → {userId: [productId1, productId2, ..., productId50]}
# es_documents.json → [{id, embedding[64], name, category, ...}]
# config.json → {embedding_dim: 64}
# two_tower_model.pt → checkpoint

!zip -r model_output.zip model_output/
files.download('model_output.zip')
```

### Bước 3: Deploy Model

```bash
# Giải nén vào resources
unzip model_output.zip -d src/main/resources/models/FoodRecommendSearchByBehavior/

# Sync ES (xóa index cũ → tạo index 64-dim → bulk index)
python scripts/recommend/sync_es_rcm_behavior.py
# Output: 483 documents indexed vào products_recommend

# Hot-reload (không cần restart app)
curl -X POST http://localhost:8080/api/admin/recommendation/reload-embeddings \
  -H "Authorization: Bearer {admin_token}"
```

---

## 5. BACKEND — JAVA SERVICE

### 5.1 Startup Flow (@PostConstruct)

```java
@PostConstruct
public void init() {
    // 1. Load productShopMap, productCategoryMap từ DB
    loadProductMappings();
    
    // 2. Load product embeddings từ ES (PRIMARY)
    //    Fallback → product_embeddings.json nếu ES down
    loadProductEmbeddingsFromEs();  // → Map<productId, float[64]> trong RAM
    
    // 3. Tính category average embeddings
    //    Dùng cho sản phẩm mới (chưa có Two-Tower embedding)
    buildCategoryAverageEmbeddings();  // → Map<categoryId, float[64]>
    
    // 4. Load precomputed_recs.json → Redis
    //    rec:model:batch:{userId} với TTL 7 ngày
    loadPrecomputedRecsToRedis();
    
    log.info("Loaded {} product embeddings, {} category averages", ...);
}

// Auto-reload mỗi 2 tiếng (pick up embeddings mới sau retrain)
@Scheduled(fixedDelay = 2 * 3600 * 1000)
public void scheduledReload() { init(); }
```

### 5.2 Real-time User Embedding

```java
// Khi user có hành vi mới
public void updateUserEmbeddingAsync(Integer userId) {
    // Query behaviors 14 ngày gần nhất từ MySQL
    List<UserBehavior> behaviors = behaviorRepo
        .findByUserIdAndCreatedAtAfter(userId, now().minusDays(14));
    
    // Weighted average của product embeddings
    float[] userEmb = new float[64];
    double totalWeight = 0;
    
    for (UserBehavior b : behaviors) {
        float[] prodEmb = getProductEmbedding(b.getProductId());
        if (prodEmb == null) continue;
        
        // Time decay: 0.7^daysAgo
        double daysAgo = Duration.between(b.getCreatedAt(), now()).toDays();
        double decay = Math.pow(0.7, daysAgo);
        double weight = b.getWeight() * decay;
        
        for (int i = 0; i < 64; i++) {
            userEmb[i] += weight * prodEmb[i];
        }
        totalWeight += weight;
    }
    
    // L2 normalize → unit vector 64-dim
    if (totalWeight > 0) {
        for (int i = 0; i < 64; i++) userEmb[i] /= totalWeight;
        float norm = computeNorm(userEmb);
        for (int i = 0; i < 64; i++) userEmb[i] /= norm;
    }
    
    // Lưu Redis TTL 6h
    redis.opsForValue().set("rec:user:embedding:" + userId,
                            Arrays.toString(userEmb),
                            Duration.ofHours(6));
}
```

**Tại sao Java không dùng User Tower ONNX?**
User Tower cần chạy qua Embedding layer + MLP mỗi khi user tương tác. Không thể update online mà không gọi Python service. Weighted average là approximation tốt: trade-off **real-time responsiveness > độ chính xác tuyệt đối**.

### 5.3 getRecommendations() — Full Flow

```java
public List<Product> getRecommendations(Integer userId, int limit) {
    
    // ① Loại products tiêu cực
    Set<Integer> negativeProducts = getNegativeProducts(userId);
    // (REVIEW_NEGATIVE, REMOVE_CART trong 30 ngày)
    
    // ② CANDIDATE GENERATION
    
    // Nguồn A: Realtime 80% — ES KNN với user embedding
    float[] userEmb = getUserEmbedding(userId);  // từ Redis hoặc tính mới
    List<Integer> realtimeCandidates = esKnnSearch(userEmb, limit * 3);
    // ES query: knn { "field": "embedding", "query_vector": userEmb, "k": limit*3 }
    
    // Nguồn B: Batch 20% — precomputed từ Redis
    List<Integer> batchCandidates = getBatchCandidates(userId);  // max limit/2
    // rec:model:batch:{userId} → [productId1, ..., productId50]
    
    // Blend: realtime trước, batch cuối (capped)
    List<Integer> pool = new ArrayList<>(realtimeCandidates);
    pool.addAll(batchCandidates.subList(0, Math.min(limit/5, batchCandidates.size())));
    pool.removeAll(negativeProducts);
    
    // ③ RERANK bằng behaviors 72h gần nhất
    Map<Integer, Double> scores = new HashMap<>();
    for (int i = 0; i < pool.size(); i++) {
        int productId = pool.get(i);
        double positionScore = 1.0 / (i + 1);  // position in pool
        
        double realtimeBoost = 0;
        for (UserBehavior b : recentBehaviors72h) {
            float[] interactedEmb = getProductEmbedding(b.getProductId());
            float[] candidateEmb  = getProductEmbedding(productId);
            double similarity = cosineSimilarity(interactedEmb, candidateEmb);
            double daysAgo = Duration.between(b.getCreatedAt(), now()).toDays();
            realtimeBoost += similarity * Math.pow(0.7, daysAgo) * b.getWeight();
        }
        
        double finalScore = 0.15 * positionScore + 0.85 * realtimeBoost;
        scores.put(productId, finalScore);
    }
    
    // Sort by finalScore DESC
    
    // ④ FALLBACK nếu pool < limit
    if (pool.size() < limit) {
        // 1. Session-based (hành vi hiện tại)
        // 2. Category-based (từ products đã tương tác)
        // 3. Shop-based
        // 4. Cold-start: trending products
    }
    
    // ⑤ Diversity: max 2 sản phẩm/shop
    return filterDiversity(sortedProducts, limit);
}
```

### 5.4 User Behavior Tracking

```java
// POST /api/user/recommendations/track
public void trackBehavior(Integer userId, TrackRequest req) {
    // Lưu nhanh vào Redis buffer (non-blocking, ~1ms)
    String key = "behavior:buffer:" + userId;
    String value = json.serialize(new UserBehavior(userId, req));
    redis.opsForList().rightPush(key, value);
    
    // Async: update user embedding
    CompletableFuture.runAsync(() -> updateUserEmbeddingAsync(userId));
}

// Background task: flush Redis buffer → MySQL mỗi 30 giây
@Scheduled(fixedDelay = 30_000)
public void flushBehaviorBuffer() {
    // LRANGE + LTRIM → batch saveAll → MySQL
    // Max lag: 30s (đủ cho embedding vì TTL 6h)
}
```

**Action types:**
```
VIEW, CLICK, SEARCH_CLICK, RECOMMEND_CLICK
ADD_CART, REMOVE_CART, WISHLIST_ADD
PURCHASE, REVIEW_POSITIVE, REVIEW_NEGATIVE
```

---

## 6. AUTO-SYNC SẢN PHẨM MỚI

```
Product tạo mới
    │ JPA @PostPersist
    ▼
EsRecommendSyncConsumer (poll Redis 5s)
    ├── 1. Fetch product từ MySQL
    ├── 2. Lấy categoryAverageEmbeddings.get(categoryId)
    │       (embedding chính xác nhất available ngay)
    ├── 3. Upsert vào products_recommend (embedding_source: "category_average")
    └── 4. Update in-memory maps: productEmbeddings, productCategoryMap
    
→ Sản phẩm mới xuất hiện trong KNN ngay sau ~5 giây
→ Không cần script thủ công

Progression tự động sau đó:
  5 giây sau tạo   → category_average embedding (tự động)
  Sau Colab retrain → Two-Tower full embedding (sau vài tuần)
```

---

## 7. MODEL OUTPUT FILES

| File | Vai trò | Java đọc? |
|---|---|---|
| `es_documents.json` | Input cho sync_es_rcm_behavior.py | Không trực tiếp |
| `precomputed_recs.json` | Batch candidates → Redis (rec:model:batch:{userId}) | Startup |
| `product_embeddings.json` | Fallback khi ES down | Startup (fallback) |
| `config.json` | embedding_dim=64 | Sync script |
| `two_tower_model.pt` | Retrain hoặc index_new_products.py | Không |
| `user_encoder.pkl` | Không dùng trực tiếp | Không |
| `product_encoder.pkl` | Không dùng trực tiếp | Không |

**ES là nguồn chính:** Java đọc product embeddings từ ES `products_recommend` lúc startup, không đọc từ JSON. JSON chỉ là fallback.

---

## 8. API ENDPOINTS

```
GET  /api/user/recommendations/for-you?limit=10
     → getRecommendations() — main personalized feed

POST /api/user/recommendations/track
     Body: { productId, actionType, sessionId, source, viewDurationSeconds }
     → trackBehavior() — log hành vi + update embedding

GET  /api/user/recommendations/cold-start?limit=10
     → Trending + top-rated (cho user mới chưa có hành vi)

POST /api/admin/recommendation/reload-embeddings
     → Hot-reload embeddings từ ES (không restart app)

GET  /api/admin/recommendation/training-data/download
     → Export JSON cho Colab training
```

---

## 9. DATABASE SCHEMA — user_behaviors

```sql
CREATE TABLE user_behaviors (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    user_id         INT NOT NULL,
    product_id      INT NOT NULL,
    action_type     VARCHAR(50) NOT NULL,   -- VIEW, CLICK, PURCHASE, etc.
    weight          DOUBLE DEFAULT 1.0,    -- action weight
    session_id      VARCHAR(100),          -- group behaviors by session
    source          VARCHAR(50),           -- HOME, SEARCH, RECOMMENDATION
    view_duration_seconds INT,             -- cho VIEW action
    quantity        INT,                   -- cho PURCHASE
    rating          INT,                   -- cho REVIEW
    category_id     INT,                   -- cache cho fast filtering
    shop_id         INT,                   -- cache cho fast filtering
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id),
    INDEX idx_created_at (created_at),
    INDEX idx_session (session_id)
);
```

---

## 10. FILES LIÊN QUAN

```
service/
  RealTimeRecommendationServiceImpl.java  ← CORE: getRecommendations + user embedding
  RecommendationDataExportServiceImpl.java ← Export training data
  UserBehaviorBufferService.java           ← Buffer behaviors Redis → MySQL
  EsRecommendSyncConsumer.java            ← Auto-sync sản phẩm mới (5s)

controller/UserController/
  RecommendationController.java           ← REST: for-you, track, cold-start

entity/
  UserBehavior.java                       ← MySQL behavior entity

scripts/recommend/
  sync_es_rcm_behavior.py                ← Sync model_output → ES 64-dim index
  index_new_products.py                  ← Optional: Two-Tower embed new products

TaiLieu/AI/RecommendByBehavior/
  REALTIME_RECOMMENDATION_SETUP.md       ← Setup guide
  RealTimeRecommendation_Train.md        ← Colab notebook full code

src/main/resources/models/FoodRecommendSearchByBehavior/model_output/
  config.json, es_documents.json, precomputed_recs.json, ...
```

---

## 11. CÁC CÂU HỎI GIÁO VIÊN CÓ THỂ HỎI

**Q: Two-Tower model là gì? Tại sao dùng kiến trúc này?**
> Two-Tower (dual encoder): User Tower và Product Tower encode độc lập → cùng embedding space 64-dim.
> Recommendation = tìm products gần nhất với user trong embedding space (cosine similarity).
> Ưu điểm: Product embeddings precomputed offline → inference = 1 vector lookup (cực nhanh).
> So với matrix factorization: Two-Tower có text features → cold start tốt hơn.

**Q: Tại sao dùng contrastive loss thay vì cross-entropy?**
> Cross-entropy cần explicit negative labels (biết product nào user KHÔNG thích) → khó thu thập.
> Contrastive loss dùng in-batch negatives: trong mỗi batch B, mỗi (user_i, product_i) là positive,
> còn B-1 pairs khác là implicit negatives. Không cần explicit negative annotation.
> Temperature 0.07: tập trung vào hard negatives — products khác nhưng semantically gần.

**Q: Java tính user embedding thế nào? Tại sao không dùng User Tower ONNX?**
> User Tower cần forward pass qua Embedding(n_users, 64) + MLP. Không thể export ONNX dễ vì
> n_users thay đổi động (user mới). Không thể update online.
> Java dùng weighted average: Σ(0.7^daysAgo × weight × product_emb) / Σ(decay × weight).
> L2 normalize về unit vector. Trade-off: real-time responsiveness > model accuracy.
> Time decay 0.7^daysAgo: hành vi hôm nay weight 1.0, hôm qua 0.7, 2 ngày trước 0.49.

**Q: Tại sao blend 80% realtime + 20% batch?**
> Batch-only: user vừa click chè nhiều nhưng pool chỉ có phở → không đáp ứng được.
> Realtime-only: không có diversity, bị narrow (filter bubble).
> 80% realtime + 20% batch: đáp ứng current interest + giữ diversity long-term.

**Q: Rerank formula (0.85 × realtime + 0.15 × position). Tại sao?**
> position_score = 1/(rank+1): candidates đầu pool từ ES KNN đã tốt → position là prior.
> realtime_boost: cosine_sim với products user tương tác gần đây (72h).
> 0.85 weight cho realtime → current behavior signal quan trọng hơn pool ordering.
> time decay trong boost: hành vi hôm nay được nhân 0.7^0 = 1.0, hôm qua 0.7^1 = 0.7.

**Q: Cold start problem — user mới chưa có hành vi thì sao?**
> Fallback chain: Session-based (session hiện tại) → Category-based → Shop-based → Trending.
> Trending: top-rated products trong 30 ngày + rating cao → đảm bảo luôn có kết quả.
> Sau 3-5 behaviors đầu tiên → bắt đầu có user embedding → recommendations cá nhân hóa.

**Q: Behavior buffer Redis có bị mất data không nếu server crash?**
> Redis không durable mặc định (AOF/RDB cần config). Max mất 30s behaviors chưa flush.
> Ảnh hưởng: embedding có thể lag 30s → chấp nhận được (TTL 6h, rebuild theo schedule).
> Không mất user behaviors quan trọng: PURCHASE/ADD_CART được flush ngay (priority flush).

**Q: ES KNN search với 64-dim vector — tốc độ như thế nào?**
> ES HNSW index: O(log N) approximate nearest neighbor → rất nhanh.
> 500 products: ~2-5ms. 50,000 products: ~10-20ms. Hoàn toàn suitable cho real-time.
> Approximate: có thể miss 1-2% nearest neighbors → chấp nhận được cho recommendation.

**Q: Sản phẩm mới có xuất hiện trong gợi ý ngay không?**
> Có. ProductEntityListener → Redis queue → EsRecommendSyncConsumer (5s) → upsert ES với
> category_average embedding. Sản phẩm mới có embedding ngay, xuất hiện trong KNN sau ~5s.
> Quality: category_average < Two-Tower → chấp nhận được. Sau retrain Colab: full quality.

**Q: Tại sao precomputed_recs có TTL 7 ngày?**
> Model retrain định kỳ (vài tuần). TTL 7d = đủ lâu để user không cảm thấy gợi ý "stale".
> Nếu user tương tác nhiều trong 7d → realtime embedding override batch candidates.
> Sau retrain → hot-reload API refresh Redis với precomputed_recs mới.
