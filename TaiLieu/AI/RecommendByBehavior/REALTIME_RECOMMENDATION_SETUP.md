# 🚀 HƯỚNG DẪN SETUP REAL-TIME RECOMMENDATION SYSTEM

## Mục lục
1. [Tổng quan hệ thống](#1-tổng-quan-hệ-thống)
2. [Yêu cầu](#2-yêu-cầu)
3. [Bước 1: Chuẩn bị Database](#bước-1-chuẩn-bị-database)
4. [Bước 2: Export Training Data](#bước-2-export-training-data)
5. [Bước 3: Train Model trên Google Colab](#bước-3-train-model-trên-google-colab)
6. [Bước 4: Download và Deploy Model](#bước-4-download-và-deploy-model)
7. [Bước 5: Sync lên Elasticsearch](#bước-5-sync-lên-elasticsearch)
8. [Bước 6: Test APIs](#bước-6-test-apis)
9. [Cách hoạt động](#7-cách-hoạt-động)
10. [Troubleshooting](#8-troubleshooting)

---

## 1. Tổng quan hệ thống

### Tại sao cần train model?

Hệ thống gợi ý cần học từ dữ liệu để biết:
- User A thích loại sản phẩm nào
- Sản phẩm nào giống sản phẩm nào
- User nào có sở thích giống nhau

### Flow tổng quan

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│   DATABASE                GOOGLE COLAB              SPRING BOOT             │
│   ────────                ────────────              ───────────             │
│                                                                             │
│   Users      ──┐                                                            │
│   Products   ──┼──► Export JSON ──► Upload ──► Train Model ──► Download    │
│   Orders     ──┘                                      │                     │
│   Behaviors                                           ▼                     │
│                                                  model_output.zip           │
│                                                       │                     │
│                                                       ▼                     │
│                                    ┌──────────────────────────────────┐     │
│                                    │  Giải nén vào resources/        │     │
│                                    │  Chạy sync_es script            │     │
│                                    │  Restart Spring Boot            │     │
│                                    └──────────────────────────────────┘     │
│                                                       │                     │
│                                                       ▼                     │
│                                              APIs sẵn sàng!                 │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Yêu cầu

### Services cần chạy:
- ✅ MySQL (database chính)
- ✅ Redis (cache user embeddings)
- ✅ Elasticsearch (lưu product embeddings, KNN search)
- ✅ Spring Boot app

### Tools cần có:
- Python 3.8+ (để chạy sync script)
- Google account (để dùng Colab)
- Postman hoặc curl (để test API)

---

## Bước 1: Chuẩn bị Database

### 1.1. Chạy migration tạo bảng `user_behaviors`

Bảng này lưu hành vi người dùng (view, click, mua hàng, review...).

```sql
-- Chạy trong MySQL Workbench hoặc DBeaver

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

### 1.2. Kiểm tra dữ liệu hiện có

```sql
-- Kiểm tra có đủ data không
SELECT COUNT(*) as total_users FROM users;
SELECT COUNT(*) as total_products FROM products;
SELECT COUNT(*) as total_orders FROM orders;
SELECT COUNT(*) as total_order_items FROM order_items;
SELECT COUNT(*) as total_reviews FROM reviews;
SELECT COUNT(*) as total_wishlists FROM wishlists;
```

**Cần có ít nhất:**
- 50+ users
- 30+ products
- 100+ order_items (hoặc behaviors)

---

## Bước 2: Export Training Data

### 2.1. Start Spring Boot

```bash
cd FoodTourApp
mvn spring-boot:run
```

### 2.2. Gọi API Export Data

Mở Postman hoặc browser, gọi API này:

```
GET http://localhost:8080/api/public/recommendation/training-data
```

**Lưu ý:** Đây là API public, không cần token.

### 2.3. Download file JSON

**Cách 1: Dùng Postman**
1. Mở Postman
2. GET `http://localhost:8080/api/public/recommendation/training-data`
3. Click "Send"
4. Click "Save Response" → "Save to a file"
5. Đặt tên: `training_data.json`

**Cách 2: Dùng curl**
```bash
curl -o training_data.json http://localhost:8080/api/public/recommendation/training-data
```

**Cách 3: Dùng Browser**
1. Mở Chrome/Firefox
2. Vào `http://localhost:8080/api/public/recommendation/training-data`
3. Ctrl+S để save, đặt tên `training_data.json`

### 2.4. Kiểm tra file

Mở file `training_data.json` xem có đúng format không:

```json
{
  "success": true,
  "data": {
    "interactions": [...],
    "products": [...],
    "users": [...]
  }
}
```

---

## Bước 3: Train Model trên Google Colab

### 3.1. Mở Google Colab

1. Vào https://colab.research.google.com
2. Đăng nhập Google account

### 3.2. Tạo Notebook mới

1. Click "File" → "New notebook"
2. Đổi tên notebook thành "FoodTour_Recommendation_Training"

### 3.3. Copy code vào Colab

1. Mở file: `src/main/resources/CollabTrain/RealTimeRecommendation_Train.md`
2. Copy **toàn bộ** code trong block ```python ... ```
3. Paste vào cell đầu tiên của Colab

### 3.4. Chạy code

1. Click nút Play ▶️ hoặc Ctrl+Enter
2. **Khi thấy "Upload file training_data.json..."** → Click "Choose Files"
3. Chọn file `training_data.json` đã download ở Bước 2
4. Đợi training (5-15 phút tùy data)

### 3.5. Download kết quả

Sau khi chạy xong, Colab sẽ tự động download file `model_output.zip`

**Nếu không tự download:**
1. Nhìn panel bên trái, click icon folder 📁
2. Tìm file `model_output.zip`
3. Click chuột phải → Download

---

## Bước 4: Download và Deploy Model

### 4.1. Giải nén model_output.zip

Giải nén vào thư mục:
```
FoodTourApp/src/main/resources/models/FoodRecommendSearchByBehavior/model_output/
```

**Windows:**
1. Chuột phải `model_output.zip` → "Extract All"
2. Chọn đường dẫn: `D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\src\main\resources\models\FoodRecommendSearchByBehavior\model_output`

**Sau khi giải nén, thư mục phải có:**
```
model_output/
├── config.json                 ← Config (embedding_dim, etc)
├── precomputed_recs.json       ← Recommendations cho từng user
├── similar_products.json       ← Sản phẩm tương tự
├── product_embeddings.json     ← Embeddings cho Spring Boot
├── es_documents.json           ← Documents cho Elasticsearch
├── two_tower_model.pt          ← Model weights (backup)
├── user_encoder.pkl            ← Encoder
└── product_encoder.pkl         ← Encoder
```

---

## Bước 5: Sync lên Elasticsearch

### 5.1. Đảm bảo Elasticsearch đang chạy

```bash
# Kiểm tra ES
curl http://localhost:9200

# Nếu dùng Docker
docker-compose up -d elasticsearch
```

### 5.2. Cài đặt Python dependencies

```bash
pip install requests
```

### 5.3. Chạy sync script

```bash
cd FoodTourApp/scripts/recommend
python sync_es_rcm_behavior.py
```

**Output mong đợi:**
```
📋 Config loaded:
   Embedding dim: 64
   Model trained at: 2024-01-15T10:30:00
   Documents to index: 150

🗑️ Deleting old index 'products_recommend'...
   Status: 200

📁 Creating index with embedding dim=64...
   ✅ Index created successfully

📤 Indexing 150 documents...
   Progress: 100/150
   Progress: 150/150

✅ Indexing complete! Success: 150, Failed: 0

📊 ES Index Stats:
   Total documents: 150

🔍 Testing KNN search...
   ✅ KNN search returned 5 results

🎉 Done! Index 'products_recommend' is ready for real-time recommendations.
```

---

## Bước 6: Test APIs

### 6.1. Restart Spring Boot

```bash
# Dừng app cũ (Ctrl+C)
# Chạy lại
mvn spring-boot:run
```

### 6.2. Test API Recommendations

**Cần đăng nhập để có token:**

```bash
# Login
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "user@test.com", "password": "123456"}'
```

Copy token từ response.

**Get Recommendations:**
```bash
curl http://localhost:8080/api/user/recommendations/for-you?limit=10 \
  -H "Authorization: Bearer YOUR_TOKEN_HERE"
```

### 6.3. Test API Similar Products (không cần token)

```bash
curl http://localhost:8080/api/user/recommendations/similar/1?limit=5
```

### 6.4. Test Track Behavior

```bash
curl -X POST http://localhost:8080/api/user/recommendations/track \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": 5,
    "actionType": "VIEW",
    "sessionId": "session123",
    "source": "RECOMMENDATION",
    "viewDurationSeconds": 30
  }'
```

---

## 7. Cách hoạt động

### Real-time Flow

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                                                                             │
│  1. User xem sản phẩm                                                       │
│        ↓                                                                    │
│  2. Frontend gọi POST /track (VIEW, productId=5)                            │
│        ↓                                                                    │
│  3. Backend lưu behavior vào DB                                             │
│        ↓                                                                    │
│  4. Async: Tính lại user embedding                                          │
│     user_embedding = weighted_average(product_embeddings đã tương tác)      │
│        ↓                                                                    │
│  5. Cache user embedding vào Redis (TTL 6 giờ)                              │
│        ↓                                                                    │
│  6. Lần sau user request /for-you:                                          │
│     - Lấy user embedding từ Redis                                           │
│     - ES KNN search: tìm products gần nhất với user embedding               │
│     - Trả về danh sách recommendations                                      │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Weights theo Action Type

| Action | Weight | Giải thích |
|--------|--------|------------|
| VIEW | 1.0 | Xem sản phẩm |
| CLICK | 1.5 | Click vào sản phẩm |
| SEARCH_CLICK | 2.0 | Click từ kết quả search |
| RECOMMEND_CLICK | 2.5 | Click từ gợi ý |
| ADD_CART | 3.0 | Thêm vào giỏ |
| PURCHASE | 5.0 | Mua hàng |
| REVIEW_POSITIVE | 4.0 | Review tốt (4-5 sao) |
| REVIEW_NEGATIVE | -2.0 | Review xấu (1-2 sao) |
| WISHLIST_ADD | 2.0 | Thêm wishlist |
| REMOVE_CART | -1.0 | Xóa khỏi giỏ |

---

## 8. Troubleshooting

### ❌ Lỗi: "product_embeddings.json not found"

**Nguyên nhân:** Chưa giải nén model_output.zip hoặc giải nén sai chỗ

**Fix:**
```bash
# Kiểm tra file có tồn tại không
ls src/main/resources/models/FoodRecommendSearchByBehavior/model_output/

# Phải thấy các file: config.json, product_embeddings.json, etc.
```

### ❌ Lỗi: "ES connection refused"

**Nguyên nhân:** Elasticsearch chưa chạy

**Fix:**
```bash
# Start ES
docker-compose up -d elasticsearch

# Đợi 30s rồi kiểm tra
curl http://localhost:9200
```

### ❌ Lỗi: "No interactions in training data"

**Nguyên nhân:** Database chưa có đủ dữ liệu

**Fix:**
1. Thêm data giả vào orders, order_items
2. Hoặc track behaviors qua API trước khi train

### ❌ Lỗi: "KNN search returned 0 results"

**Nguyên nhân:** Embedding dimension không khớp

**Fix:**
1. Kiểm tra `config.json` → `embedding_dim` phải = 64
2. Chạy lại `sync_es_rcm_behavior.py`

### ❌ Recommendations trả về rỗng

**Nguyên nhân:** User chưa có behavior nào

**Fix:**
1. Track vài behaviors cho user đó
2. Hoặc system sẽ fallback về top-rated products

---

## 9. Re-train Model

Khi nào cần train lại?
- Có thêm nhiều sản phẩm mới
- Có thêm nhiều users/behaviors mới
- Muốn cải thiện chất lượng gợi ý

**Steps:**
1. Lặp lại từ Bước 2 (Export data mới)
2. Train lại trên Colab
3. Download model_output.zip mới
4. Giải nén đè lên folder cũ
5. Chạy sync_es script
6. Restart Spring Boot

---

## 10. Checklist Setup

- [ ] MySQL có bảng `user_behaviors`
- [ ] Có ít nhất 50 users, 30 products, 100 interactions
- [ ] Export được `training_data.json`
- [ ] Train thành công trên Colab
- [ ] Download được `model_output.zip`
- [ ] Giải nén vào `resources/models/FoodRecommendSearchByBehavior/model_output/`
- [ ] Elasticsearch đang chạy
- [ ] Chạy `sync_es_rcm_behavior.py` thành công
- [ ] Spring Boot restart
- [ ] Test API `/for-you` trả về data
- [ ] Test API `/similar/{id}` trả về data

---

## Liên hệ

Nếu gặp vấn đề, kiểm tra logs:
```bash
# Spring Boot logs
tail -f logs/application.log

# Elasticsearch logs
docker logs elasticsearch
```
