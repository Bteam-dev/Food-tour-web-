# 🍜 FoodTourApp - Hệ Thống Recommend Thông Minh

> Tài liệu này ghi lại toàn bộ quá trình xây dựng hệ thống gợi ý sản phẩm kiểu YouTube cho FoodTourApp.  
> Bao gồm: fix lỗi export CSV, train model trên Google Colab (free), index Elasticsearch, tích hợp Spring Boot.

---

## 📋 Mục Lục

1. [Tổng Quan Hệ Thống](#1-tổng-quan-hệ-thống)
2. [Kiến Trúc Hybrid Recommend](#2-kiến-trúc-hybrid-recommend)
3. [Fix Lỗi Export CSV MySQL Workbench](#3-fix-lỗi-export-csv-mysql-workbench)
4. [Export Data Từ MySQL](#4-export-data-từ-mysql)
5. [Train Model Trên Google Colab](#5-train-model-trên-google-colab)
6. [Index Elasticsearch](#6-index-elasticsearch)
7. [Tích Hợp Spring Boot](#7-tích-hợp-spring-boot)
8. [API Endpoints](#8-api-endpoints)
9. [Retrain Định Kỳ](#9-retrain-định-kỳ)
10. [Troubleshooting](#10-troubleshooting)

---

## 1. Tổng Quan Hệ Thống

### Hệ thống gợi ý dựa theo 3 yếu tố:

| Yếu tố | Ví dụ |
|--------|-------|
| **Tên sản phẩm đã mua** | Hay mua Phở → gợi ý Bún, Hủ Tiếu, Miến |
| **Shop hay mua** | Hay mua shop A → ưu tiên món shop A + shop tương tự |
| **Đánh giá cao** | Rating cao những món Phở, Bún → gợi ý món tương tự |
| **Wishlist** | Đã yêu thích món nào → gợi ý món cùng loại |

### Stack công nghệ:

```
Google Colab (T4 GPU) → Train ALS + Embeddings
Elasticsearch 8.11     → Vector KNN Search  
Spring Boot            → API Backend
MySQL 8.0 (Docker)     → Database
```

---

## 2. Kiến Trúc Hybrid Recommend

### Công thức YouTube-style:

```
Kết quả = 70% Precomputed (ổn định) + 30% ES Realtime (luôn tươi)
```

### Luồng hoạt động:

```
User gọi API /for-you
        │
        ├─► Có trong precomputed_recs.json?
        │         │
        │    CÓ ──┤── Shuffle theo seed thời gian (30 phút đổi 1 lần)
        │         │   → Lấy 70% top precomputed
        │         │
        │   KHÔNG ─┤── Cold start: trả top rated (Bayesian)
        │
        ├─► ES KNN Search (30% realtime)
        │         │
        │         ├── Seed = sản phẩm mua gần nhất + wishlist gần nhất
        │         └── Exclude sản phẩm đã mua / đã có trong 70%
        │
        └─► Merge xen kẽ: pre, pre, es, pre, pre, es...
                  │
                  └── Giới hạn tối đa 2 món/shop → đảm bảo đa dạng
```

### Điểm hybrid (lúc train Colab):

```
Score = 0.45 × CF_score + 0.35 × CB_score + 0.20 × Popularity_score
```

| Thành phần | Giải thích |
|-----------|-----------|
| **CF (Collaborative Filtering)** | ALS matrix factorization - học từ hành vi mua chung của nhiều users |
| **CB (Content-Based)** | Sentence embedding tiếng Việt - tìm món giống nhau theo tên/nguyên liệu/dinh dưỡng |
| **Popularity** | Bayesian rating = rating × log(total_reviews + 1) |

### Diversity Re-ranking (MMR):

Sau khi có top candidates, áp dụng **Maximal Marginal Relevance**:
- Penalize món quá giống nhau (cosine similarity cao)
- Penalize cùng shop (−0.15 mỗi món trùng shop)
- Đảm bảo danh sách đa dạng thay vì toàn phở hay toàn shop A

---

## 3. Fix Lỗi Export CSV MySQL Workbench

### Lỗi gốc:

```
UnicodeEncodeError: 'charmap' codec can't encode character '\u1edf'
```

**Nguyên nhân:** Workbench dùng encoding CP1252 (Windows), không hỗ trợ tiếng Việt.

### Fix: Kết nối thẳng từ Python vào MySQL (không dùng wizard export)

Dùng ngrok tạo tunnel → Colab kết nối trực tiếp vào MySQL Docker:

```python
!pip install pyngrok pymysql
from pyngrok import ngrok

tcp_tunnel = ngrok.connect(3307, "tcp")
print("Tunnel URL:", tcp_tunnel.public_url)
# → tcp://0.tcp.ngrok.io:XXXXX
```

Sau đó dùng `pd.read_sql()` đọc thẳng từ DB, hoàn toàn tránh được vấn đề encoding.

### Nếu muốn export file TSV thủ công:

1. Chạy query trong Workbench
2. Chuột phải vào kết quả → **Export Resultset**
3. Chỉnh:
   - **Field Separator**: nhấn phím Tab (1 lần)
   - **String Quoting**: Never
   - **Line Ending**: LF
4. Lưu đuôi `.tsv`

> **Lý do dùng TAB:** Data tiếng Việt chứa dấu phẩy trong JSON (ingredients, nutrition_info) → CSV bị vỡ cột. TAB không bao giờ xuất hiện trong data tiếng Việt.

---

## 4. Export Data Từ MySQL

### Query 1: orders_behavior (hành vi mua hàng)

```sql
SELECT 
    o.user_id,
    oi.product_id,
    REPLACE(REPLACE(p.name, '\t', ' '), '\n', ' ') as product_name,
    REPLACE(REPLACE(COALESCE(p.ingredients,''), '\t', ' '), '\n', ' ') as ingredients,
    REPLACE(REPLACE(COALESCE(p.nutrition_info,''), '\t', ' '), '\n', ' ') as nutrition_info,
    REPLACE(REPLACE(COALESCE(p.tags,''), '\t', ' '), '\n', ' ') as tags,
    p.category_id,
    p.shop_id,
    oi.quantity,
    o.order_status,
    COALESCE(r.rating, 0) as user_rating,
    COUNT(*) OVER (PARTITION BY o.user_id, oi.product_id) as purchase_count
FROM orders o
JOIN order_items oi ON o.id = oi.order_id
JOIN products p ON oi.product_id = p.id
LEFT JOIN reviews r ON r.order_id = o.id 
    AND r.reviewable_id = oi.product_id 
    AND r.reviewable_type = 'product'
WHERE o.order_status = 'delivered'
ORDER BY o.user_id, purchase_count DESC;
```

### Query 2: wishlist

```sql
SELECT 
    w.user_id,
    w.product_id,
    REPLACE(REPLACE(p.name, '\t', ' '), '\n', ' ') as name,
    REPLACE(REPLACE(COALESCE(p.ingredients,''), '\t', ' '), '\n', ' ') as ingredients,
    REPLACE(REPLACE(COALESCE(p.nutrition_info,''), '\t', ' '), '\n', ' ') as nutrition_info,
    REPLACE(REPLACE(COALESCE(p.tags,''), '\t', ' '), '\n', ' ') as tags,
    p.category_id,
    p.shop_id,
    COALESCE(p.rating, 0) as avg_rating
FROM wishlists w
JOIN products p ON w.product_id = p.id;
```

### Query 3: products

```sql
SELECT 
    id,
    REPLACE(REPLACE(name, '\t', ' '), '\n', ' ') as name,
    REPLACE(REPLACE(COALESCE(description,''), '\t', ' '), '\n', ' ') as description,
    REPLACE(REPLACE(COALESCE(ingredients,''), '\t', ' '), '\n', ' ') as ingredients,
    REPLACE(REPLACE(COALESCE(nutrition_info,''), '\t', ' '), '\n', ' ') as nutrition_info,
    REPLACE(REPLACE(COALESCE(tags,''), '\t', ' '), '\n', ' ') as tags,
    category_id,
    shop_id,
    COALESCE(rating, 0) as rating,
    COALESCE(total_reviews, 0) as total_reviews,
    price
FROM products 
WHERE is_available = true;
```

---

## 5. Train Model Trên Google Colab

### Setup:

1. Vào [colab.research.google.com](https://colab.research.google.com)
2. **Runtime → Change runtime type → T4 GPU → Save**
3. Upload 3 file TSV (hoặc kết nối MySQL qua ngrok)

### Cell 1: Install

```python
!pip install sentence-transformers scikit-learn pandas numpy implicit pymysql pyngrok
```

### Cell 2: Load data (qua ngrok)

```python
import pymysql
import pandas as pd

conn = pymysql.connect(
    host="0.tcp.ngrok.io",  # thay bằng host ngrok của mày
    port=12345,              # thay bằng port ngrok
    user="minhngosen196",
    password="minhngosen196",
    database="food_tour_app",
    charset='utf8mb4'
)

df_products = pd.read_sql("SELECT id, name, description, ingredients, nutrition_info, tags, category_id, shop_id, COALESCE(rating,0) as rating, COALESCE(total_reviews,0) as total_reviews, price FROM products WHERE is_available=true", conn)
df_orders   = pd.read_sql("SELECT o.user_id, oi.product_id, p.name as product_name, p.ingredients, p.nutrition_info, p.tags, p.category_id, p.shop_id, oi.quantity, o.order_status, COALESCE(r.rating,0) as user_rating, COUNT(*) OVER (PARTITION BY o.user_id, oi.product_id) as purchase_count FROM orders o JOIN order_items oi ON o.id=oi.order_id JOIN products p ON oi.product_id=p.id LEFT JOIN reviews r ON r.order_id=o.id AND r.reviewable_id=oi.product_id AND r.reviewable_type='product' WHERE o.order_status='delivered'", conn)
df_wishlist = pd.read_sql("SELECT w.user_id, w.product_id, p.name, p.ingredients, p.nutrition_info, p.tags, p.category_id, p.shop_id, COALESCE(p.rating,0) as avg_rating FROM wishlists w JOIN products p ON w.product_id=p.id", conn)
conn.close()
```

### Cell 3: Tính interaction score

```python
def compute_interaction_scores(df_orders, df_wishlist):
    interactions = []
    for _, row in df_orders.iterrows():
        purchase_score = min(row['purchase_count'] * 2.0, 10.0)
        rating_bonus   = (row['user_rating'] / 5.0) * 3.0 if row['user_rating'] > 0 else 0
        interactions.append({'user_id': row['user_id'], 'product_id': row['product_id'],
                              'score': purchase_score + rating_bonus})
    for _, row in df_wishlist.iterrows():
        interactions.append({'user_id': row['user_id'], 'product_id': row['product_id'], 'score': 3.0})
    df = pd.DataFrame(interactions)
    df = df.groupby(['user_id','product_id'])['score'].sum().reset_index()
    df['score'] = df['score'].clip(upper=15.0)
    return df

df_interactions = compute_interaction_scores(df_orders, df_wishlist)
```

### Cell 4: Train ALS (Collaborative Filtering)

```python
import implicit
from scipy.sparse import csr_matrix
import numpy as np

user_ids    = df_interactions['user_id'].unique()
product_ids = df_interactions['product_id'].unique()
user2idx    = {u: i for i, u in enumerate(user_ids)}
product2idx = {p: i for i, p in enumerate(product_ids)}
idx2product = {i: p for p, i in product2idx.items()}
idx2user    = {i: u for u, i in user2idx.items()}

rows = df_interactions['user_id'].map(user2idx)
cols = df_interactions['product_id'].map(product2idx)
item_user_matrix = csr_matrix(
    (df_interactions['score'].values, (cols, rows)),
    shape=(len(product_ids), len(user_ids))
)

model_als = implicit.als.AlternatingLeastSquares(factors=64, regularization=0.1, iterations=30)
model_als.fit(item_user_matrix)
```

### Cell 5: Content embeddings (hiểu tiếng Việt)

```python
from sentence_transformers import SentenceTransformer

def build_product_text(row):
    parts = []
    if pd.notna(row.get('name')):         parts.append(f"Tên: {row['name']}")
    if pd.notna(row.get('description')):  parts.append(f"Mô tả: {row['description']}")
    if pd.notna(row.get('ingredients')):  parts.append(f"Nguyên liệu: {row['ingredients']}")
    if pd.notna(row.get('nutrition_info')):parts.append(f"Dinh dưỡng: {row['nutrition_info']}")
    if pd.notna(row.get('tags')):         parts.append(f"Tags: {row['tags']}")
    return " | ".join(parts)

df_products['full_text'] = df_products.apply(build_product_text, axis=1)

# Model multilingual hiểu tiếng Việt tốt, nhẹ, free
embed_model      = SentenceTransformer('paraphrase-multilingual-MiniLM-L12-v2')
product_embeddings = embed_model.encode(df_products['full_text'].tolist(),
                                         batch_size=32, show_progress_bar=True,
                                         normalize_embeddings=True)
# Shape: (số sản phẩm, 384)
```

### Cell 6: Build user profiles

```python
from sklearn.metrics.pairwise import cosine_similarity

product_id_to_embed_idx = {int(row['id']): idx for idx, row in df_products.iterrows()}

def build_user_profile(user_id):
    user_inters = df_interactions[df_interactions['user_id'] == user_id]
    if user_inters.empty: return None
    weighted_vecs, weights = [], []
    for _, row in user_inters.iterrows():
        pid = row['product_id']
        if pid in product_id_to_embed_idx:
            idx = product_id_to_embed_idx[pid]
            weighted_vecs.append(product_embeddings[idx] * row['score'])
            weights.append(row['score'])
    if not weighted_vecs: return None
    profile = np.sum(weighted_vecs, axis=0) / sum(weights)
    norm = np.linalg.norm(profile)
    return profile / norm if norm > 0 else profile

user_profiles = {uid: build_user_profile(uid) for uid in user_ids
                 if build_user_profile(uid) is not None}
```

### Cell 7: Hybrid scoring + MMR diversity

> Công thức: Score = 0.45×CF + 0.35×CB + 0.20×Popularity  
> Sau đó MMR re-ranking để đa dạng shop và loại món

*(Xem full code trong notebook đã lưu)*

### Cell 8: Save và download

```python
import pickle, json, os

os.makedirs('/content/model_output', exist_ok=True)

with open('/content/model_output/als_model.pkl', 'wb') as f:
    pickle.dump(model_als, f)

np.save('/content/model_output/product_embeddings.npy', product_embeddings)

with open('/content/model_output/mappings.pkl', 'wb') as f:
    pickle.dump({
        'user2idx': {int(k): int(v) for k, v in user2idx.items()},
        'product2idx': {int(k): int(v) for k, v in product2idx.items()},
        'idx2product': {int(k): int(v) for k, v in idx2product.items()},
        'idx2user': {int(k): int(v) for k, v in idx2user.items()},
        'product_id_to_embed_idx': {int(k): int(v) for k, v in product_id_to_embed_idx.items()},
        'user_profiles': {int(k): v for k, v in user_profiles.items()},
    }, f)

# Convert numpy int64 → Python int trước khi JSON serialize
recs_serializable = {str(int(uid)): [int(p) for p in pids]
                     for uid, pids in all_recommendations.items()}
with open('/content/model_output/precomputed_recs.json', 'w') as f:
    json.dump(recs_serializable, f, ensure_ascii=False)

df_products.to_csv('/content/model_output/products_indexed.csv', index=False)

!zip -r /content/model_output.zip /content/model_output/
from google.colab import files
files.download('/content/model_output.zip')
```

---

## 6. Index Elasticsearch

### File `sync_es_rcm_behavior.py`

Chạy **1 lần** sau khi train xong để đưa embeddings vào ES:

```python
import numpy as np
import pandas as pd
import requests

ES_URL     = "http://localhost:9200"
INDEX_NAME = "products_recommend"

mapping = {
    "mappings": {
        "properties": {
            "product_id":  {"type": "integer"},
            "name":        {"type": "text"},
            "shop_id":     {"type": "integer"},
            "category_id": {"type": "integer"},
            "rating":      {"type": "float"},
            "embedding": {
                "type": "dense_vector", "dims": 384,
                "index": True, "similarity": "cosine"
            }
        }
    }
}

requests.delete(f"{ES_URL}/{INDEX_NAME}")
requests.put(f"{ES_URL}/{INDEX_NAME}", json=mapping)

embeddings  = np.load('model_output/product_embeddings.npy')
df_products = pd.read_csv('model_output/products_indexed.csv')

for row_idx in range(len(df_products)):
    row = df_products.iloc[row_idx]
    doc = {
        "product_id":  int(row['id']),
        "name":        str(row['name']),
        "shop_id":     int(row['shop_id']),
        "category_id": int(row['category_id']),
        "rating":      float(row['rating']),
        "embedding":   embeddings[row_idx].tolist()
    }
    requests.post(f"{ES_URL}/{INDEX_NAME}/_doc/{int(row['id'])}", json=doc)

requests.post(f"{ES_URL}/{INDEX_NAME}/_refresh")
r = requests.get(f"{ES_URL}/{INDEX_NAME}/_count")
print(f"✅ Indexed {r.json().get('count')} documents")
```

**Chạy:**
```bash
pip install requests numpy pandas
python sync_es_rcm_behavior.py
```

---

## 7. Tích Hợp Spring Boot

### Cấu trúc file:

```
src/main/
├── java/com/example/FoodTourApp/
│   ├── controller/UserController/
│   │   └── RecommendationController.java
│   ├── DTO/RecommendDTO/
│   │   └── ProductRecommendDTO.java
│   ├── service/
│   │   └── RecommendationService.java (interface)
│   └── service/impl/
│       └── RecommendationServiceImpl.java
└── resources/
    └── models/
        └── content.model_output/
            └── precomputed_recs.json   ← file quan trọng nhất
```

### Lưu ý quan trọng:

- `precomputed_recs.json` được load vào RAM lúc Spring Boot khởi động (`@PostConstruct`)
- ES KNN dùng `query_vector_id` để lookup embedding theo product_id (không cần gửi vector)
- `RestTemplate` gọi ES trực tiếp, không cần Spring Data Elasticsearch
- Diversity đảm bảo tối đa **2 món/shop** trong danh sách recommend

### Docker compose (ES):

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.11.1
  container_name: foodtour_es
  environment:
    - discovery.type=single-node
    - xpack.security.enabled=false
    - ES_JAVA_OPTS=-Xms512m -Xmx512m
  ports:
    - "9200:9200"
  volumes:
    - ../ElasticSearch_data:/usr/share/elasticsearch/data
```

---

## 8. API Endpoints

### GET /api/user/recommendations/for-you

Gợi ý sản phẩm cho user đang đăng nhập - YouTube style.

**Request:**
```
GET /api/user/recommendations/for-you?limit=10
Authorization: Bearer <JWT_TOKEN>
```

**Response:**
```json
{
  "success": true,
  "data": [
    {
      "id": 4,
      "name": "Phở bò tái",
      "price": 50000.00,
      "discountPrice": null,
      "rating": 4.8,
      "totalReviews": 23,
      "imageUrls": "[\"url1\",\"url2\"]",
      "shopId": 1,
      "shopName": "Quán Phở Ngon",
      "categoryName": "Món nước",
      "recommendReason": "Vì bạn hay mua Phở bò tái"
    },
    {
      "id": 8,
      "name": "Bún bò Huế",
      "price": 55000.00,
      "rating": 4.6,
      "recommendReason": "Nguyên liệu tương tự Phở bò tái"
    }
  ]
}
```

**Hành vi:**
- Gọi sau 30 phút → thứ tự xáo trộn khác
- Vừa đặt đơn xong gọi lại → 30% ES realtime đã cập nhật theo đơn mới
- User mới (chưa có đơn) → trả top rated (cold start)

---

### GET /api/user/recommendations/similar/{productId}

Gợi ý món tương tự - dùng trên trang chi tiết sản phẩm.

**Request:**
```
GET /api/user/recommendations/similar/4?limit=6
Authorization: Bearer <JWT_TOKEN>
```

**Response:** tương tự `/for-you` nhưng `recommendReason` = "Cùng danh mục" nếu ES không tìm được.

---

## 9. Retrain Định Kỳ

### Khi nào cần retrain:

| Tình huống | Cần retrain không? |
|-----------|-------------------|
| User vừa đặt đơn mới | ❌ Không - ES realtime tự cập nhật |
| User thêm wishlist | ❌ Không - ES realtime tự cập nhật |
| Thứ tự recommend thay đổi | ❌ Không - tự shuffle 30 phút/lần |
| Có nhiều user mới tích lũy đủ lịch sử | ✅ Cần retrain |
| Thêm sản phẩm mới vào DB | ✅ Cần retrain + re-index ES |
| Muốn model học pattern mới | ✅ Cần retrain |

### Tần suất khuyến nghị:

```
Mới ra mắt (< 100 users)   → Retrain mỗi tuần
Đang phát triển (100-1000)  → Retrain mỗi 2 tuần  
Ổn định (> 1000 users)      → Retrain mỗi tháng
```

### Quy trình retrain (5 bước, ~10 phút):

```
Bước 1: Export lại 3 query SQL → 3 file TSV mới
Bước 2: Upload lên Google Colab
Bước 3: Runtime → Run All
Bước 4: Download model_output.zip
Bước 5a: Copy precomputed_recs.json vào resources/models/content.model_output/
Bước 5b: (Nếu có sản phẩm mới) Chạy lại sync_es_rcm_behavior.py
Bước 5c: Restart Spring Boot
```

> **Không cần sửa bất kỳ dòng code nào khi retrain.**

---

## 10. Troubleshooting

### Lỗi: `UnicodeEncodeError: charmap codec`

**Nguyên nhân:** Export CSV từ Workbench bị lỗi encoding tiếng Việt.  
**Fix:** Kết nối thẳng MySQL từ Python qua ngrok, dùng `pd.read_sql()`.

---

### Lỗi: `Expected N fields in line X, saw M`

**Nguyên nhân:** Data chứa dấu phẩy trong JSON (ingredients, nutrition_info) → CSV bị vỡ cột.  
**Fix:** Dùng TAB separator thay vì dấu phẩy. Lưu file đuôi `.tsv`.

---

### Lỗi: `filter_already_liked` is not a valid keyword argument

**Nguyên nhân:** Version `implicit` mới đổi API.  
**Fix:**
```python
# Bỏ: filter_already_liked=True
# Dùng:
model_als.recommend(
    user_idx, user_items, N=len(product_ids),
    filter_items=[product2idx[p] for p in seen_products if p in product2idx]
)
```

---

### Lỗi: `Object of type int64 is not JSON serializable`

**Nguyên nhân:** Numpy `int64` không serialize được sang JSON.  
**Fix:**
```python
recs_serializable = {
    str(int(uid)): [int(p) for p in pids]
    for uid, pids in all_recommendations.items()
}
json.dump(recs_serializable, f)
```

---

### Lỗi: `Connection Refused port 9200`

**Nguyên nhân:** Elasticsearch chưa chạy.  
**Fix:**
```bash
docker ps                          # Kiểm tra container
docker compose up -d elasticsearch # Khởi động ES
curl http://localhost:9200         # Kiểm tra ES sống chưa
```

Nếu lỗi `vm.max_map_count too low` (Windows):
```bash
# Mở PowerShell Administrator
wsl -d docker-desktop sysctl -w vm.max_map_count=262144
```

---

### Lỗi: `precomputed_recs.json cannot be opened because it does not exist`

**Nguyên nhân:** Chưa copy file vào đúng thư mục.  
**Fix:** Copy file vào:
```
src/main/resources/models/content.model_output/precomputed_recs.json
```

---

### ES index count = 0 sau khi index xong

**Nguyên nhân:** ES chưa refresh.  
**Fix:** Thêm vào cuối script:
```python
requests.post(f"{ES_URL}/{INDEX_NAME}/_refresh")
```

---

### Recommend chỉ có 2 users dù DB có 3

**Nguyên nhân:** Bình thường. Chỉ users có đơn hàng `delivered` mới được train.  
User chưa có đơn → tự động dùng cold start (top rated).  
**Fix:** Thêm đơn hàng delivered cho user đó rồi retrain.

---

## 📁 Files Quan Trọng

| File | Vị trí | Mô tả |
|------|--------|-------|
| `precomputed_recs.json` | `resources/models/content.model_output/` | Recommend precomputed cho từng user |
| `product_embeddings.npy` | `resources/models/content.model_output/` | Vector embedding 384 chiều mỗi sản phẩm |
| `als_model.pkl` | `resources/models/content.model_output/` | ALS model đã train |
| `sync_es_rcm_behavior.py` | `TaiLieu/FileSync/` | Script index ES |
| `RecommendationServiceImpl.java` | `service/impl/` | Business logic chính |
| `RecommendationController.java` | `controller/UserController/` | API endpoints |

---

*Tài liệu được viết bởi quá trình build thực tế, không phải lý thuyết.*  
*Data ít hay nhiều đều hoạt động - thêm data sau retrain là recommend ngày càng chính xác hơn.*
