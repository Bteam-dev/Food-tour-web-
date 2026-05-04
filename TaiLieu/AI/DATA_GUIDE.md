# Hướng dẫn xem & chèn Demo Data

## 1. Data lưu ở đâu?

| Dữ liệu | Lưu ở đâu | Ghi như nào |
|---------|-----------|-------------|
| **UserBehavior** | MySQL — bảng `user_behaviors` | Redis buffer → flush vào MySQL mỗi 30 giây |
| **SearchHistory** | MySQL — bảng `search_histories` | Ghi trực tiếp khi user search (đã login) |
| **SearchAnalytics** | Elasticsearch — index `search_analytics` | Ghi async (non-blocking) mỗi lượt search/click |

> **Lưu ý Redis buffer (UserBehavior):** Sau khi gọi API track, data nằm trong Redis tối đa 30 giây trước khi được flush vào MySQL. Nếu muốn thấy ngay trong MySQL, chờ 30s hoặc restart app (flush sẽ chạy khi startup).

---

## 2. Xem data hiện có

### UserBehavior — MySQL

```sql
-- Tổng số records
SELECT COUNT(*) FROM user_behaviors;

-- Xem 20 records gần nhất
SELECT
    ub.id,
    u.email,
    p.name AS product_name,
    ub.action_type,
    ub.weight,
    ub.session_id,
    ub.source,
    ub.view_duration_seconds,
    ub.quantity,
    ub.created_at
FROM user_behaviors ub
JOIN users u ON ub.user_id = u.id
JOIN products p ON ub.product_id = p.id
ORDER BY ub.created_at DESC
LIMIT 20;

-- Thống kê theo loại hành vi
SELECT action_type, COUNT(*) as count, ROUND(AVG(weight), 2) as avg_weight
FROM user_behaviors
GROUP BY action_type
ORDER BY count DESC;

-- Xem hành vi của 1 user cụ thể
SELECT p.name, ub.action_type, ub.weight, ub.created_at
FROM user_behaviors ub
JOIN products p ON ub.product_id = p.id
WHERE ub.user_id = 1
ORDER BY ub.created_at DESC
LIMIT 30;
```

### SearchHistory — MySQL

```sql
-- Tổng số records
SELECT COUNT(*) FROM search_histories;

-- Xem lịch sử tìm kiếm gần đây
SELECT
    sh.id,
    u.email,
    sh.query_text,
    sh.query_normalized,
    sh.result_count,
    sh.searched_at
FROM search_histories sh
JOIN users u ON sh.user_id = u.id
ORDER BY sh.searched_at DESC
LIMIT 20;

-- Số lịch sử mỗi user
SELECT u.email, COUNT(*) as history_count
FROM search_histories sh
JOIN users u ON sh.user_id = u.id
GROUP BY sh.user_id, u.email
ORDER BY history_count DESC;
```

### SearchAnalytics — Elasticsearch

```bash
# Đếm tổng documents
curl http://localhost:9200/search_analytics/_count

# Xem 5 documents gần nhất
curl "http://localhost:9200/search_analytics/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 5,
    "sort": [{"searched_at": {"order": "desc"}}]
  }'

# Đếm số clicks có trong data
curl "http://localhost:9200/search_analytics/_count?pretty" \
  -H "Content-Type: application/json" \
  -d '{"query": {"exists": {"field": "clicked_product_id"}}}'

# Top queries (trending)
curl "http://localhost:9200/search_analytics/_search?pretty" \
  -H "Content-Type: application/json" \
  -d '{
    "size": 0,
    "aggs": {
      "top_queries": {
        "terms": {"field": "query_normalized", "size": 10}
      }
    }
  }'

# Xem data trong Redis buffer (chưa flush vào MySQL)
redis-cli LLEN user:behavior:buffer
redis-cli LRANGE user:behavior:buffer 0 4
```

---

## 3. Chèn demo data bằng Script Python

> **Khi nào dùng:** Cần nhiều data để demo recommendation, trending, Colab training mà không muốn click thủ công hàng trăm lần.

### Cài đặt

```bash
pip install pymysql requests
```

### Bước 1: Lấy danh sách users và products thực trong DB

Chạy query này trước để biết ID hợp lệ:

```sql
-- Lấy user IDs
SELECT id, email FROM users WHERE role = 'USER' LIMIT 20;

-- Lấy product IDs
SELECT id, name, category_id, shop_id FROM products WHERE is_available = 1 LIMIT 50;
```

### Bước 2: Script chèn demo data

Lưu file sau thành `insert_demo_data.py`, chỉnh `USER_IDS` và `PRODUCT_IDS` theo kết quả query ở trên:

```python
# insert_demo_data.py
# Chèn thẳng vào MySQL + ES (bypass API, không qua Redis buffer)
# Dùng cho demo/test, KHÔNG dùng trên production thật

import pymysql
import requests
import json
import random
from datetime import datetime, timedelta

# ── CONFIG ────────────────────────────────────────────────────────────────────
MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 3307,
    'user': 'root',           # đổi theo .env của bạn
    'password': 'yourpass',   # đổi theo .env của bạn
    'database': 'food_tour_app',
    'charset': 'utf8mb4',
}

ES_URL = 'http://localhost:9200'

# Đổi các ID này theo DB thực của bạn (chạy query SELECT ở Bước 1)
USER_IDS     = [1, 2, 3, 4, 5]           # user IDs tồn tại trong DB
PRODUCT_IDS  = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]  # product IDs tồn tại trong DB
CATEGORY_IDS = [1, 2, 3]                 # category IDs
SHOP_IDS     = [1, 2, 3]                 # shop IDs

# Số lượng records cần chèn
NUM_USER_BEHAVIORS  = 500   # ~500 user behavior records
NUM_SEARCH_HISTORY  = 100   # ~100 search history records (per user: max 50)
NUM_SEARCH_ANALYTICS = 1000 # ~1000 search analytics records (ES)

# ── DATA MẪU ──────────────────────────────────────────────────────────────────
QUERIES = [
    'phở bò', 'phở gà', 'phở cuốn',
    'bánh mì', 'bánh mì thịt', 'bánh mì trứng',
    'bún bò', 'bún chả', 'bún riêu',
    'cơm tấm', 'cơm chiên', 'cơm gà',
    'gỏi cuốn', 'chả giò', 'nem rán',
    'hủ tiếu', 'mì quảng', 'cao lầu',
    'bánh xèo', 'bánh khọt', 'bánh cuốn',
    'lẩu bò', 'lẩu gà', 'lẩu hải sản',
    'trà sữa', 'cà phê', 'nước mía',
    'kem tươi', 'chè thái', 'bánh flan',
]

SESSIONS = [f'demo-session-{i:03d}' for i in range(1, 51)]  # 50 fake sessions

ACTION_TYPES = [
    # (action_type, weight, probability)
    ('VIEW',            1.0,  0.40),
    ('CLICK',           1.5,  0.20),
    ('SEARCH_CLICK',    2.0,  0.10),
    ('ADD_CART',        3.0,  0.10),
    ('WISHLIST_ADD',    2.0,  0.08),
    ('PURCHASE',        5.0,  0.07),
    ('REVIEW_POSITIVE', 4.0,  0.03),
    ('RECOMMEND_CLICK', 2.5,  0.02),
]

SOURCES = ['SEARCH', 'HOME', 'CATEGORY', 'RECOMMENDATION', 'DIRECT']


def random_dt(days_back=90):
    """Trả về datetime ngẫu nhiên trong N ngày gần đây."""
    offset = timedelta(
        days=random.randint(0, days_back),
        hours=random.randint(0, 23),
        minutes=random.randint(0, 59),
    )
    return datetime.now() - offset


def pick_action():
    r = random.random()
    cumulative = 0.0
    for action, weight, prob in ACTION_TYPES:
        cumulative += prob
        if r <= cumulative:
            return action, weight
    return ACTION_TYPES[0][:2]


# ── 1. UserBehavior → MySQL ────────────────────────────────────────────────────
def insert_user_behaviors(conn, count):
    print(f"\n[1/3] Inserting {count} UserBehavior records into MySQL...")

    sql = """
        INSERT INTO user_behaviors
            (user_id, product_id, action_type, weight, session_id, source,
             view_duration_seconds, quantity, rating, category_id, shop_id, created_at)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
    """

    batch = []
    for i in range(count):
        action, weight = pick_action()
        user_id    = random.choice(USER_IDS)
        product_id = random.choice(PRODUCT_IDS)
        session_id = random.choice(SESSIONS)
        source     = random.choice(SOURCES)
        duration   = random.randint(5, 300) if action == 'VIEW' else None
        quantity   = random.randint(1, 3)   if action in ('PURCHASE', 'ADD_CART') else None
        rating     = random.randint(4, 5)   if action == 'REVIEW_POSITIVE' else None
        cat_id     = random.choice(CATEGORY_IDS)
        shop_id    = random.choice(SHOP_IDS)
        created_at = random_dt(90)

        batch.append((user_id, product_id, action, weight, session_id, source,
                      duration, quantity, rating, cat_id, shop_id, created_at))

        # Batch insert mỗi 100 records
        if len(batch) >= 100:
            with conn.cursor() as cur:
                cur.executemany(sql, batch)
            conn.commit()
            batch = []
            print(f"  → {i + 1}/{count} inserted...")

    if batch:
        with conn.cursor() as cur:
            cur.executemany(sql, batch)
        conn.commit()

    print(f"  ✅ Done: {count} UserBehavior records")


# ── 2. SearchHistory → MySQL ───────────────────────────────────────────────────
def insert_search_history(conn, count_per_user):
    print(f"\n[2/3] Inserting SearchHistory records (up to {count_per_user} per user)...")

    sql = """
        INSERT IGNORE INTO search_histories
            (user_id, query_text, query_normalized, result_count, searched_at)
        VALUES (%s, %s, %s, %s, %s)
    """

    total = 0
    for user_id in USER_IDS:
        # Lấy tối đa count_per_user queries ngẫu nhiên (không trùng normalized)
        chosen = random.sample(QUERIES, min(count_per_user, len(QUERIES)))
        batch = []
        for q in chosen:
            normalized = q.lower().strip()
            result_count = random.randint(0, 50)
            searched_at  = random_dt(30)
            batch.append((user_id, q, normalized, result_count, searched_at))

        with conn.cursor() as cur:
            cur.executemany(sql, batch)
        conn.commit()
        total += len(batch)

    print(f"  ✅ Done: {total} SearchHistory records (max {count_per_user}/user)")


# ── 3. SearchAnalytics → Elasticsearch ────────────────────────────────────────
def insert_search_analytics(count):
    print(f"\n[3/3] Inserting {count} SearchAnalytics docs into Elasticsearch...")

    bulk_lines = []
    for i in range(count):
        query_text  = random.choice(QUERIES)
        normalized  = query_text.lower().strip()
        user_id     = random.choice(USER_IDS + [None])  # guest hoặc logged-in
        session_id  = random.choice(SESSIONS)
        result_count = random.randint(0, 50)
        searched_at = random_dt(90).strftime('%Y-%m-%dT%H:%M:%S')

        doc = {
            'user_id':           user_id,
            'query_text':        query_text,
            'query_normalized':  normalized,
            'result_count':      result_count,
            'session_id':        session_id,
            'searched_at':       searched_at,
        }

        # 30% chance có click
        if random.random() < 0.30:
            doc['clicked_product_id'] = random.choice(PRODUCT_IDS)
            doc['click_position']     = random.randint(1, 10)

        bulk_lines.append(json.dumps({'index': {'_index': 'search_analytics'}}))
        bulk_lines.append(json.dumps(doc))

        # Bulk index mỗi 200 docs
        if len(bulk_lines) >= 400:  # 400 lines = 200 doc pairs
            _bulk_index(bulk_lines, i + 1, count)
            bulk_lines = []

    if bulk_lines:
        _bulk_index(bulk_lines, count, count)

    print(f"  ✅ Done: {count} SearchAnalytics docs in ES")


def _bulk_index(lines, progress, total):
    body = '\n'.join(lines) + '\n'
    resp = requests.post(
        f'{ES_URL}/_bulk',
        headers={'Content-Type': 'application/x-ndjson'},
        data=body.encode('utf-8'),
        timeout=30,
    )
    if resp.status_code not in (200, 201):
        print(f"  ⚠️ Bulk index error: {resp.text[:200]}")
    else:
        result = resp.json()
        if result.get('errors'):
            print(f"  ⚠️ Some docs failed to index at {progress}/{total}")
        else:
            print(f"  → {progress}/{total} indexed...")


# ── MAIN ───────────────────────────────────────────────────────────────────────
if __name__ == '__main__':
    print("=" * 60)
    print("FoodTourApp — Demo Data Inserter")
    print("=" * 60)

    # Kết nối MySQL
    conn = pymysql.connect(**MYSQL_CONFIG)
    try:
        insert_user_behaviors(conn, NUM_USER_BEHAVIORS)
        insert_search_history(conn, count_per_user=20)
    finally:
        conn.close()

    # Elasticsearch (không cần connection pool)
    insert_search_analytics(NUM_SEARCH_ANALYTICS)

    print("\n" + "=" * 60)
    print("✅ Demo data inserted successfully!")
    print("=" * 60)
    print(f"  UserBehavior  : {NUM_USER_BEHAVIORS} records → MySQL user_behaviors")
    print(f"  SearchHistory : {len(USER_IDS) * 20} records → MySQL search_histories")
    print(f"  SearchAnalytics: {NUM_SEARCH_ANALYTICS} docs  → ES search_analytics")
    print()
    print("Kiểm tra:")
    print("  mysql> SELECT COUNT(*) FROM user_behaviors;")
    print("  mysql> SELECT COUNT(*) FROM search_histories;")
    print("  curl http://localhost:9200/search_analytics/_count")
```

---

### Bước 3: Chỉnh config và chạy

```bash
# 1. Mở file, sửa 4 dòng:
#    MYSQL_CONFIG password
#    USER_IDS     → copy từ: SELECT id FROM users WHERE role='USER' LIMIT 10;
#    PRODUCT_IDS  → copy từ: SELECT id FROM products WHERE is_available=1 LIMIT 20;
#    CATEGORY_IDS / SHOP_IDS → copy từ SELECT id FROM categories / shops LIMIT 5;

# 2. Chạy
pip install pymysql requests
python insert_demo_data.py
```

**Output mẫu:**
```
============================================================
FoodTourApp — Demo Data Inserter
============================================================

[1/3] Inserting 500 UserBehavior records into MySQL...
  → 100/500 inserted...
  → 200/500 inserted...
  → 500/500 inserted...
  ✅ Done: 500 UserBehavior records

[2/3] Inserting SearchHistory records (up to 20 per user)...
  ✅ Done: 100 SearchHistory records (max 20/user)

[3/3] Inserting 1000 SearchAnalytics docs into Elasticsearch...
  → 200/1000 indexed...
  → 1000/1000 indexed...
  ✅ Done: 1000 SearchAnalytics docs in ES

============================================================
✅ Demo data inserted successfully!
============================================================
  UserBehavior  : 500 records → MySQL user_behaviors
  SearchHistory : 100 records → MySQL search_histories
  SearchAnalytics: 1000 docs  → ES search_analytics
```

---

## 4. Kiểm tra sau khi chèn

```bash
# ── MySQL ──────────────────────────────────────────────
mysql -h localhost -P 3307 -u root -p food_tour_app

mysql> SELECT COUNT(*) FROM user_behaviors;
mysql> SELECT action_type, COUNT(*) FROM user_behaviors GROUP BY action_type;
mysql> SELECT COUNT(*) FROM search_histories;

# ── Elasticsearch ──────────────────────────────────────
# Tổng docs
curl http://localhost:9200/search_analytics/_count

# Số clicks (quan trọng cho PhoBERT training)
curl -X GET "http://localhost:9200/search_analytics/_count" \
  -H "Content-Type: application/json" \
  -d '{"query": {"exists": {"field": "clicked_product_id"}}}'

# ── Redis buffer (UserBehavior chưa flush) ──────────────
redis-cli LLEN user:behavior:buffer
# → 0 nếu đã flush hết (script chèn trực tiếp vào MySQL nên không qua Redis)

# ── API test ───────────────────────────────────────────
# Trending (lấy từ ES)
curl http://localhost:8080/api/public/search/trending?limit=10

# Export CSV để train PhoBERT (cần login admin trước)
curl -H "Authorization: Bearer <TOKEN>" \
     "http://localhost:8080/api/admin/search/analytics/export?days=90" \
     -o search_analytics.csv && wc -l search_analytics.csv
```

---

## 5. Xóa demo data (khi cần reset)

```sql
-- Xóa UserBehavior demo (xóa tất cả, hoặc xóa theo created_at gần đây)
DELETE FROM user_behaviors;
-- hoặc chỉ xóa record được tạo hôm nay:
DELETE FROM user_behaviors WHERE DATE(created_at) = CURDATE();

-- Xóa SearchHistory demo
DELETE FROM search_histories;
```

```bash
# Xóa SearchAnalytics trong ES
curl -X DELETE http://localhost:9200/search_analytics
# App sẽ tự tạo lại index khi restart (@PostConstruct)

# Hoặc xóa từng phần (theo ngày):
curl -X POST "http://localhost:9200/search_analytics/_delete_by_query" \
  -H "Content-Type: application/json" \
  -d '{
    "query": {
      "range": {
        "searched_at": {"gte": "2026-04-30", "lte": "2026-04-30"}
      }
    }
  }'
```

---

## 6. Tóm tắt nhanh

```
Muốn xem data?
  UserBehavior  → mysql: SELECT * FROM user_behaviors LIMIT 20;
  SearchHistory → mysql: SELECT * FROM search_histories LIMIT 20;
  SearchAnalytics → curl http://localhost:9200/search_analytics/_search?pretty&size=5

Muốn chèn demo?
  → Sửa USER_IDS, PRODUCT_IDS trong insert_demo_data.py
  → python insert_demo_data.py

Muốn xem Redis buffer (UserBehavior chưa flush)?
  → redis-cli LLEN user:behavior:buffer
  → redis-cli LRANGE user:behavior:buffer 0 4

Muốn export để train PhoBERT?
  → curl -H "Authorization: Bearer TOKEN" localhost:8080/api/admin/search/analytics/export
```
