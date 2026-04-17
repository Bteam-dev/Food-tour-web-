# Hybrid Search - Hướng dẫn đầy đủ

## Tổng quan kiến trúc

```
User query
    │
    ▼
Spring Boot (ProductSearchServiceImpl)
    │
    ├─ Redis cache ── "phobert:emb:{query}" (TTL 30 min)
    │       │
    │       └─ Cache miss → gọi PhoBERT server
    │
    ├─ PhoBERT Server (Google Colab + Cloudflare tunnel)
    │       └─ POST /embed → 768-dim vector
    │
    └─ Elasticsearch (Hybrid Query)
            ├─ BM25 full-text (multi_match, fuzzy, edge_ngram)
            └─ Cosine similarity (dense_vector)
                   Score = bm25_w * BM25 + sem_w * cosine
                   (default: 0.3 * BM25 + 0.7 * cosine)
```

**Hai chế độ hoạt động:**
- **BM25 only** (`phobert.enabled=false`): full-text + fuzzy, không cần Colab, hoạt động ngay
- **Hybrid** (`phobert.enabled=true`): BM25 + semantic cosine, hiểu tiếng Việt sai chính tả

> **Bước 1 → 3 bắt buộc cho cả hai chế độ.** Bước 4 → 6 chỉ cần nếu muốn dùng Hybrid.

---

## Yêu cầu

| Thành phần | Phiên bản |
|---|---|
| Elasticsearch | 8.x (có dense_vector support) |
| Python | 3.8+ |
| Python packages | `pip install elasticsearch pymysql` |
| Redis | 6+ |
| Google Colab | GPU runtime — chỉ cần cho Hybrid |

---

## Bước 1: Export data từ MySQL

### Export `products.csv`

> **Không dùng MySQL Workbench để export CSV.** Workbench không quote đúng các text field chứa dấu phẩy (`description`, `shop_name`...) và không quote JSON arrays → `pd.read_csv` lỗi `ParserError: Expected N fields, saw N+1`.
>
> **Dùng script Python dưới đây chạy trên máy local** — Python dùng `csv.QUOTE_ALL` nên mọi field đều được quote đúng, không bao giờ bị lỗi parse.

Lưu file sau thành `export_products.py`, chạy trên máy local (không phải Colab):

```python
# export_products.py — chạy local: python export_products.py
import pymysql
import csv
import json
import re
from datetime import datetime

MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 3307,
    'user': 'minhngosen196',
    'password': 'minhngosen196',
    'database': 'food_tour_app',
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor
}

def json_to_pipe(value):
    """Chuyển JSON array → pipe-separated string.
    ["hot","traditional"] → hot|traditional
    None / [] / '' → ''
    """
    if not value:
        return ''
    s = str(value).strip()
    if not s or s == '[]':
        return ''
    try:
        arr = json.loads(s)
        if isinstance(arr, list):
            return '|'.join(str(x) for x in arr if x)
    except (json.JSONDecodeError, TypeError):
        pass
    # Fallback: regex extract
    items = re.findall(r'"([^"]+)"', s)
    return '|'.join(items)

conn = pymysql.connect(**MYSQL_CONFIG)
try:
    with conn.cursor() as cursor:
        cursor.execute("""
            SELECT
                p.id, p.name, p.description,
                p.price, p.discount_price,
                p.image_urls, p.ingredients, p.nutrition_info, p.tags,
                p.preparation_time, p.stock_quantity,
                p.rating, p.total_reviews, p.is_available, p.created_at,
                s.shop_name, s.city AS shop_city,
                c.name AS category_name
            FROM products p
            LEFT JOIN shops s ON p.shop_id = s.id
            LEFT JOIN categories c ON p.category_id = c.id
            WHERE p.is_available = 1
              AND s.is_verified = 1
              AND s.is_active = 1
            ORDER BY p.created_at DESC
        """)
        rows = cursor.fetchall()

    with open('products.csv', 'w', newline='', encoding='utf-8-sig') as f:
        fieldnames = ['id','name','description','price','discount_price',
                      'image_urls','ingredients','nutrition_info','tags',
                      'preparation_time','stock_quantity','rating',
                      'total_reviews','is_available','created_at',
                      'shop_name','shop_city','category_name']
        writer = csv.DictWriter(f, fieldnames=fieldnames, quoting=csv.QUOTE_ALL)
        writer.writeheader()
        for row in rows:
            # Convert JSON arrays → pipe-separated
            for col in ['image_urls', 'ingredients', 'nutrition_info', 'tags']:
                row[col] = json_to_pipe(row.get(col))
            # Convert datetime → string
            if isinstance(row.get('created_at'), datetime):
                row['created_at'] = row['created_at'].isoformat()
            writer.writerow(row)

    print(f"Exported {len(rows)} products → products.csv")
finally:
    conn.close()
```

```bash
pip install pymysql
python export_products.py
# → tạo ra products.csv sẵn sàng upload lên Colab
```

### Export `search_analytics.csv`

```sql
SELECT
    id,
    user_id,
    query_text,
    query_normalized,
    clicked_product_id,
    click_position,
    result_count,
    session_id,
    searched_at
FROM search_analytics
WHERE query_text IS NOT NULL AND query_text != ''
ORDER BY searched_at DESC
LIMIT 50000;
```

> Cần ít nhất 100 rows click data để train PhoBERT có ý nghĩa. Nếu chưa đủ thì dùng BM25 only trước.

---

## Bước 2: Tạo ES index + sync sản phẩm

```bash
cd D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp

pip install elasticsearch pymysql

# Lần đầu hoặc khi thay đổi mapping → recreate
python scripts/search/sync_es_search.py --recreate-index

# Các lần sau chỉ cần sync
python scripts/search/sync_es_search.py --full

# Kiểm tra
python scripts/search/sync_es_search.py --verify
```

**Kết quả:**
```
[OK] Synced 150 products (0 with embeddings)
   Total documents: 150
   With embeddings: 0    ← bình thường, chưa có embedding
```

---

## Bước 3: Sync suggestions từ DB + chạy app (BM25 only)

```bash
# Tạo suggestion index từ MySQL
python scripts/search/sync_es_suggestions.py --recreate-index --from-db

# Kiểm tra
python scripts/search/sync_es_suggestions.py --verify
```

Cấu hình `application.properties`:

```properties
phobert.enabled=false
```

**→ App đã hoạt động với BM25 only. Dừng ở đây nếu không cần Hybrid.**

Test thử:
```bash
curl "http://localhost:8080/api/public/products?keyword=phở&page=0&size=5"
curl "http://localhost:8080/api/public/search/trending?limit=10"
```

---

## Bước 4: Train PhoBERT trên Google Colab (chỉ cần cho Hybrid)

Vào [colab.research.google.com](https://colab.research.google.com), tạo notebook mới, chọn **Runtime → Change runtime type → T4 GPU**.

### Cell 1 - Cài dependencies

```python
!pip install transformers torch pandas numpy tqdm scikit-learn lightgbm flask --quiet
!pip install tabulate
print("Done")
```

### Cell 2 - Config

```python
CONFIG = {
    "MODEL_NAME":         "vinai/phobert-base-v2",
    "MAX_LENGTH":         128,
    "BATCH_SIZE":         16,
    "EPOCHS":             3,
    "LEARNING_RATE":      2e-5,
    "VECTOR_DIM":         768,
    "OUTPUT_DIR":         "./phobert_foodtour",
    "TRENDING_WEIGHT":    0.7,
    "TRENDING_THRESHOLD": 1.5,
    "ES_WEIGHT_MAX":      10000,
    "ES_WEIGHT_MIN":      1,
    "EPSILON":            1e-6,
}
print("Config loaded")
```

### Cell 3 - Upload data

```python
from google.colab import files

print("Upload products.csv")
up = files.upload()
for fn, data in up.items():
    with open(fn, 'wb') as f: f.write(data)

print("\nUpload search_analytics.csv")
up2 = files.upload()
for fn, data in up2.items():
    with open(fn, 'wb') as f: f.write(data)
```

### Cell 4 - Helper functions

```python
import pandas as pd
import numpy as np
import json
import re
import random
from collections import Counter, defaultdict
from datetime import datetime, timedelta

_VN_MAP = {
    'à':'a','á':'a','ả':'a','ã':'a','ạ':'a',
    'â':'a','ầ':'a','ấ':'a','ẩ':'a','ậ':'a',
    'ă':'a','ằ':'a','ắ':'a','ẳ':'a','ặ':'a',
    'è':'e','é':'e','ẻ':'e','ẽ':'e','ẹ':'e',
    'ê':'e','ề':'e','ế':'e','ể':'e','ệ':'e',
    'ì':'i','í':'i','ỉ':'i','ị':'i',
    'ò':'o','ó':'o','ỏ':'o','õ':'o','ọ':'o',
    'ô':'o','ồ':'o','ố':'o','ổ':'o','ộ':'o',
    'ơ':'o','ờ':'o','ớ':'o','ở':'o','ợ':'o',
    'ù':'u','ú':'u','ủ':'u','ụ':'u',
    'ư':'u','ừ':'u','ứ':'u','ử':'u','ự':'u',
    'ỳ':'y','ý':'y','ỷ':'y','ỹ':'y','ỵ':'y','đ':'d',
}

def remove_vn_accent(text):
    if not text: return ''
    return ''.join(_VN_MAP.get(c, c) for c in text.lower())

def parse_pipe(x):
    """Parse pipe-separated string thành list. VD: 'hot|traditional' → ['hot','traditional']"""
    s = str(x).strip() if x and str(x).strip() not in ('', 'nan') else ''
    if not s:
        return []
    return [t.strip() for t in s.split('|') if t.strip()]

def build_product_text(row):
    name = str(row.get('name') or '')
    desc = str(row.get('description') or '')
    tags = row.get('tags_str', '')
    ingredients = row.get('ingredients_str', '')
    category = str(row.get('category_name') or '')
    return f"{name} {desc} {tags} {ingredients} {category}".strip()

print("Helpers ready")
```

### Cell 5 - Load products

```python
from tabulate import tabulate

# engine='python' xử lý được các edge case mà C parser bỏ sót
# (vd: field chứa newline, quote lồng nhau, dấu phẩy trong text)
df_products = pd.read_csv(
    "products.csv",
    encoding="utf-8-sig",
    engine="python",
    on_bad_lines="warn",   # bỏ qua row lỗi thay vì crash, in warning
)
df_products.columns = [c.strip().lower().replace(' ', '_') for c in df_products.columns]

# Fillna cho các cột text
for col in ['description', 'tags', 'ingredients', 'nutrition_info', 'category_name', 'image_urls']:
    if col in df_products.columns:
        df_products[col] = df_products[col].fillna('')

# Parse pipe-separated arrays → list (vd: "hot|traditional" → ["hot","traditional"])
df_products['tags_clean']        = df_products['tags'].apply(parse_pipe)
df_products['ingredients_clean'] = df_products['ingredients'].apply(parse_pipe)
df_products['images_clean']      = df_products['image_urls'].apply(parse_pipe)

df_products['tags_str']        = df_products['tags_clean'].apply(lambda x: ' '.join(x))
df_products['ingredients_str'] = df_products['ingredients_clean'].apply(lambda x: ' '.join(x))
df_products['product_text']    = df_products.apply(build_product_text, axis=1)

print(f"DONE: {len(df_products)} products")
print(tabulate(
    df_products[['id','name','price','rating','shop_name','category_name','tags_str','ingredients_str']].head(5),
    headers='keys', tablefmt='fancy_grid', showindex=False
))
```

### Cell 6 - Load analytics

```python
df_analytics = pd.read_csv("search_analytics.csv", encoding='utf-8-sig')
df_analytics.columns = [c.strip().lower().replace(' ', '_') for c in df_analytics.columns]
df_analytics['query_text']  = df_analytics.get('query_text', pd.Series(dtype=str)).fillna('')
df_analytics['searched_at'] = pd.to_datetime(df_analytics.get('searched_at'), errors='coerce')
df_analytics = df_analytics[df_analytics['query_text'].str.strip() != '']

print(f"{len(df_analytics)} analytics rows")
print(f"Clicks: {df_analytics['clicked_product_id'].notna().sum()}")
```

### Cell 7 - Load PhoBERT

```python
import torch
from transformers import AutoTokenizer, AutoModel
from torch import nn

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
print(f"Device: {device}")

tokenizer = AutoTokenizer.from_pretrained(CONFIG["MODEL_NAME"])
model     = AutoModel.from_pretrained(CONFIG["MODEL_NAME"]).to(device)
print(f"PhoBERT loaded")

def mean_pooling(model_output, attention_mask):
    token_emb = model_output.last_hidden_state
    mask_expanded = attention_mask.unsqueeze(-1).expand(token_emb.size()).float()
    return torch.sum(token_emb * mask_expanded, 1) / torch.clamp(mask_expanded.sum(1), min=1e-9)

def get_embedding(text, model, tokenizer, device):
    enc = tokenizer(
        text, max_length=CONFIG['MAX_LENGTH'],
        padding='max_length', truncation=True, return_tensors='pt'
    ).to(device)
    with torch.no_grad():
        out = model(**enc)
    emb = mean_pooling(out, enc['attention_mask'])
    emb = nn.functional.normalize(emb, p=2, dim=1)
    return emb.cpu().numpy()[0].tolist()
```

### Cell 8 - Chuẩn bị training data

```python
from torch.utils.data import Dataset, DataLoader

df_clicks = df_analytics[df_analytics['clicked_product_id'].notna()].copy()
df_clicks['clicked_product_id'] = df_clicks['clicked_product_id'].astype(int)

product_text_map = dict(zip(df_products['id'].astype(int), df_products['product_text']))
product_id_list = list(product_text_map.keys())

pairs = []
for _, row in df_clicks.iterrows():
    query = str(row['query_text']).strip()
    pid   = int(row['clicked_product_id'])
    if pid in product_text_map and query:
        pairs.append({'query': query, 'product_text': product_text_map[pid], 'label': 1.0})

neg_pairs = [{'query': p['query'], 'product_text': product_text_map[random.choice(product_id_list)], 'label': 0.0} for p in pairs]
all_pairs = pairs + neg_pairs
random.shuffle(all_pairs)
print(f"{len(all_pairs)} pairs ({len(pairs)} positive, {len(neg_pairs)} negative)")

class SearchDataset(Dataset):
    def __init__(self, pairs, tokenizer, max_len):
        self.pairs = pairs; self.tokenizer = tokenizer; self.max_len = max_len
    def __len__(self): return len(self.pairs)
    def __getitem__(self, idx):
        p = self.pairs[idx]
        q_enc = self.tokenizer(p['query'], max_length=self.max_len, padding='max_length', truncation=True, return_tensors='pt')
        d_enc = self.tokenizer(p['product_text'], max_length=self.max_len, padding='max_length', truncation=True, return_tensors='pt')
        return {
            'q_input_ids': q_enc['input_ids'].squeeze(), 'q_attention_mask': q_enc['attention_mask'].squeeze(),
            'd_input_ids': d_enc['input_ids'].squeeze(), 'd_attention_mask': d_enc['attention_mask'].squeeze(),
            'label': torch.tensor(p['label'], dtype=torch.float),
        }

dataloader = DataLoader(SearchDataset(all_pairs, tokenizer, CONFIG['MAX_LENGTH']), batch_size=CONFIG['BATCH_SIZE'], shuffle=True)
print(f"DataLoader ready: {len(dataloader)} batches")
```

### Cell 9 - Train

```python
from transformers import get_linear_schedule_with_warmup
from torch.optim import AdamW
from tqdm import tqdm

optimizer = AdamW(model.parameters(), lr=CONFIG['LEARNING_RATE'])
scheduler = get_linear_schedule_with_warmup(optimizer, num_warmup_steps=0,
    num_training_steps=len(dataloader) * CONFIG['EPOCHS'])
loss_fn = nn.CosineEmbeddingLoss()

model.train()
for epoch in range(CONFIG['EPOCHS']):
    total_loss = 0
    for batch in tqdm(dataloader, desc=f"Epoch {epoch+1}/{CONFIG['EPOCHS']}"):
        q_out = model(input_ids=batch['q_input_ids'].to(device), attention_mask=batch['q_attention_mask'].to(device))
        d_out = model(input_ids=batch['d_input_ids'].to(device), attention_mask=batch['d_attention_mask'].to(device))
        q_emb = nn.functional.normalize(mean_pooling(q_out, batch['q_attention_mask'].to(device)), p=2, dim=1)
        d_emb = nn.functional.normalize(mean_pooling(d_out, batch['d_attention_mask'].to(device)), p=2, dim=1)
        labels = batch['label'].to(device)
        target = torch.where(labels > 0.5, torch.ones_like(labels), torch.full_like(labels, -1.0))
        loss = loss_fn(q_emb, d_emb, target)
        optimizer.zero_grad(); loss.backward(); optimizer.step(); scheduler.step()
        total_loss += loss.item()
    print(f"Epoch {epoch+1} - Avg Loss: {total_loss/len(dataloader):.4f}")

print("Training complete!")
# Loss mong đợi: Epoch 1 ~0.3, Epoch 3 ~0.1
```

### Cell 10 - Save model

```python
import os, shutil
from google.colab import files

os.makedirs(CONFIG['OUTPUT_DIR'], exist_ok=True)
model.save_pretrained(CONFIG['OUTPUT_DIR'])
tokenizer.save_pretrained(CONFIG['OUTPUT_DIR'])
shutil.make_archive("phobert_foodtour", 'zip', CONFIG['OUTPUT_DIR'])
files.download("phobert_foodtour.zip")
print("Downloaded phobert_foodtour.zip")
```

### Cell 11 - Generate product embeddings

```python
model.eval()
print("Generating embeddings...")

all_embeddings = []
for _, row in tqdm(df_products.iterrows(), total=len(df_products)):
    all_embeddings.append(get_embedding(row['product_text'], model, tokenizer, device))

df_products['embedding'] = all_embeddings
print(f"{len(all_embeddings)} embeddings generated, dim={len(all_embeddings[0])}")
```

### Cell 12 - Tính trending + build suggestions

```python
def normalize_query(text):
    if not text: return ''
    text = text.lower().strip()
    text = remove_vn_accent(text)
    text = re.sub(r'\s+', ' ', re.sub(r'[^\w\s]', '', text))
    return text.strip()

now = df_analytics['searched_at'].max().replace(tzinfo=None)
cut_1d  = now - timedelta(days=1)
cut_7d  = now - timedelta(days=7)
cut_15d = now - timedelta(days=15)

stats = defaultdict(lambda: {'texts': [], 'total': 0, 'c1d': 0, 'c7d': 0, 'c15d': 0, 'last': None})
for _, row in df_analytics.iterrows():
    raw_q = str(row.get('query_text', '')).strip()
    if not raw_q: continue
    norm = normalize_query(raw_q)
    ts = row.get('searched_at')
    if pd.isna(ts): ts = now
    s = stats[norm]
    s['texts'].append(raw_q); s['total'] += 1
    if ts >= cut_1d:  s['c1d'] += 1
    if ts >= cut_7d:  s['c7d'] += 1
    if ts >= cut_15d: s['c15d'] += 1
    if s['last'] is None or ts > s['last']: s['last'] = ts

suggestion_docs = []
epsilon = CONFIG['EPSILON']
print("Generating suggestion embeddings...")
for norm, s in tqdm(stats.items()):
    recent_count = s['c15d']
    avg_recent = recent_count / 15.0 if recent_count > 0 else 0
    multiplier = (s['c1d'] + s['c7d']*0.5) / (avg_recent + epsilon) if avg_recent >= epsilon else 1.0
    final_score = max(recent_count * (1 + CONFIG['TRENDING_WEIGHT'] * (multiplier - 1)), 0.0)
    best_text = Counter(s['texts']).most_common(1)[0][0]
    emb = get_embedding(best_text, model, tokenizer, device)
    suggestion_docs.append({
        'query_text': best_text, 'query_normalized': norm,
        'no_accent': remove_vn_accent(best_text), 'embedding': json.dumps(emb),
        'total_searches': s['total'], 'searches_1d': s['c1d'],
        'searches_7d': s['c7d'], 'searches_15d': s['c15d'],
        'trending_multiplier': round(multiplier, 4),
        'is_trending': multiplier >= CONFIG['TRENDING_THRESHOLD'],
        'es_weight': round(final_score, 4),
        'last_searched_at': s['last'].strftime('%Y-%m-%d') if s['last'] else '',
    })

if suggestion_docs:
    max_score = max(d['es_weight'] for d in suggestion_docs)
    for d in suggestion_docs:
        nw = d['es_weight'] / max_score if max_score > 0 else 0
        d['es_weight'] = max(CONFIG['ES_WEIGHT_MIN'], min(CONFIG['ES_WEIGHT_MAX'],
            int(CONFIG['ES_WEIGHT_MIN'] + nw * (CONFIG['ES_WEIGHT_MAX'] - CONFIG['ES_WEIGHT_MIN']))))

print(f"\n{len(suggestion_docs)} suggestions")
for d in sorted(suggestion_docs, key=lambda x: x['es_weight'], reverse=True)[:10]:
    badge = "TREND" if d['is_trending'] else "     "
    print(f"[{badge}] [{d['es_weight']:5d}] {d['query_text']:28} | total={d['total_searches']:3d}")
```

### Cell 13 - Export embeddings.csv + suggestions.csv

```python
import csv
from google.colab import files

# Product embeddings
with open("embeddings.csv", "w", newline='', encoding='utf-8-sig') as f:
    writer = csv.DictWriter(f, fieldnames=['product_id', 'embedding'])
    writer.writeheader()
    for _, row in df_products.iterrows():
        writer.writerow({'product_id': int(row['id']), 'embedding': json.dumps(row['embedding'])})
files.download("embeddings.csv")
print(f"Exported {len(df_products)} product embeddings")

# Suggestions
fieldnames = ['query_text','query_normalized','no_accent','embedding',
              'total_searches','searches_1d','searches_7d','searches_15d',
              'trending_multiplier','is_trending','es_weight','last_searched_at']
with open("suggestions.csv", "w", newline='', encoding='utf-8-sig') as f:
    writer = csv.DictWriter(f, fieldnames=fieldnames)
    writer.writeheader()
    for d in suggestion_docs: writer.writerow(d)
files.download("suggestions.csv")
print(f"Exported {len(suggestion_docs)} suggestions")
```

---

## Bước 5: Index embeddings vào Elasticsearch

Sau khi download `embeddings.csv` và `suggestions.csv` từ Colab:

```bash
# Copy files vào thư mục dataset D:\Project\BackEnd\FoodTourApp_BE\Dataset\DatasetByRecommendSearch
# Index product embeddings (recreate để đảm bảo mapping đúng)
python D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\scripts\search\sync_es_search.py --recreate-index --embeddings D:\Project\BackEnd\FoodTourApp_BE\Dataset\DatasetByRecommendSearch\embeddings.csv

python D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\scripts\search\sync_es_suggestions.py --recreate-index --from-csv D:\Project\BackEnd\FoodTourApp_BE\Dataset\DatasetByRecommendSearch\suggestions.csv

python D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\scripts\search\sync_es_search.py --verify
```

**Kết quả sau khi xong:**
```
   Total documents: 150
   With embeddings: 150    ← tất cả đều có embedding
```

---

## Bước 6: Start Flask server + Cloudflare tunnel

### Cell 14 - Flask server

```python
from flask import Flask, request, jsonify
import threading

embed_app = Flask(__name__)
model.eval()

@embed_app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok", "model": CONFIG["MODEL_NAME"], "dim": CONFIG["VECTOR_DIM"]})

@embed_app.route("/embed", methods=["POST"])
def embed_endpoint():
    data = request.get_json()
    text = data.get("text", "").strip()
    if not text:
        return jsonify({"embedding": [0.0] * CONFIG["VECTOR_DIM"]})
    enc = tokenizer(text, max_length=CONFIG["MAX_LENGTH"], padding='max_length',
                    truncation=True, return_tensors='pt').to(device)
    with torch.no_grad():
        out = model(**enc)
    emb = nn.functional.normalize(mean_pooling(out, enc['attention_mask']), p=2, dim=1)
    return jsonify({"embedding": emb.cpu().numpy()[0].tolist()})

t = threading.Thread(target=lambda: embed_app.run(host='0.0.0.0', port=5001, threaded=False, use_reloader=False))
t.daemon = True; t.start()

import time; time.sleep(2)
print("Flask server running on port 5001")
```

### Cell 15 - Cloudflare tunnel

```python
import subprocess, time, re

!wget -q https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O cloudflared
!chmod +x cloudflared

proc = subprocess.Popen(['./cloudflared', 'tunnel', '--url', 'http://localhost:5001'],
    stdout=subprocess.PIPE, stderr=subprocess.STDOUT)

tunnel_url = None
for _ in range(60):
    line = proc.stdout.readline().decode('utf-8', errors='ignore')
    if line.strip(): print(line.strip())
    match = re.search(r'https://[a-z0-9\-]+\.trycloudflare\.com', line)
    if match:
        tunnel_url = match.group(0); break
    time.sleep(1)

if tunnel_url:
    print(f"\n{'='*60}\nTUNNEL URL: {tunnel_url}\n{'='*60}")
    print(f"\nDán vào application.properties:")
    print(f"   phobert.server-url={tunnel_url}")
    print(f"   phobert.enabled=true")
else:
    print("Không lấy được URL, chạy lại cell này")
```

### Cell 16 - Test

```python
import requests, numpy as np

resp = requests.get(f"{tunnel_url}/health", timeout=15)
print(f"Health: {resp.json()}")

emb1 = requests.post(f"{tunnel_url}/embed", json={"text": "pho bo"}, timeout=30).json()['embedding']
emb2 = requests.post(f"{tunnel_url}/embed", json={"text": "poh"}, timeout=30).json()['embedding']

cos_sim = np.dot(emb1, emb2) / (np.linalg.norm(emb1) * np.linalg.norm(emb2))
print(f"dim={len(emb1)}, cosine('pho bo', 'poh')={cos_sim:.4f}")
# Mong đợi > 0.7 — PhoBERT hiểu "poh" ≈ "phở bò"
```

---

## Bước 7: Cập nhật application.properties + restart

```properties
phobert.server-url=https://xxx.trycloudflare.com
phobert.enabled=true

# Tune weights nếu cần
search.hybrid.bm25-weight=0.3
search.hybrid.semantic-weight=0.7
```

Restart Spring Boot → Hybrid mode hoạt động.

---

## Verify sau khi xong

```bash
# Đếm documents có embedding
curl "http://localhost:9200/foodtour_products_search/_count" \
  -H "Content-Type: application/json" \
  -d '{"query":{"exists":{"field":"embedding"}}}'

# Test hybrid search (gõ sai chính tả)
curl "http://localhost:8080/api/public/products?keyword=poh&page=0&size=5"
curl "http://localhost:8080/api/public/search/suggest?q=poh&limit=8"

# Kiểm tra Redis cache
redis-cli KEYS "phobert:emb:*"
redis-cli TTL "phobert:emb:pho bo"
```

---

## Troubleshooting

**ES không kết nối:** `curl http://localhost:9200` — kiểm tra Docker đang chạy chưa.

**Script lỗi "No module":** `pip install elasticsearch pymysql`

**MySQL sai port:** kiểm tra `MYSQL_CONFIG` trong `sync_es_search.py` — Docker thường dùng port `3307`.

**Tunnel expired** (Cloudflare tunnel trên Colab tự hết sau ~8 giờ):
1. Chạy lại Cell 14 (Flask server)
2. Chạy lại Cell 15 (tunnel)
3. Copy URL mới vào `application.properties` → restart app

Khi PhoBERT offline → backend tự fallback BM25, log hiện `falling back to BM25 only`.

**Stale embedding cache** (sau khi retrain PhoBERT):
```bash
redis-cli --scan --pattern "phobert:emb:*" | xargs redis-cli DEL
```

---

## Re-train khi có data mới

1. Export lại `search_analytics.csv` từ MySQL (cần thêm ~1000 clicks mới)
2. Chạy lại Colab Cell 1 → 13
3. Download `embeddings.csv`, `suggestions.csv`
4. Chạy lại Bước 5 để index
5. Xóa embedding cache Redis
