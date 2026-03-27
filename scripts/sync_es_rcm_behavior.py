import numpy as np
import pandas as pd
import requests
import json

ES_URL = "http://localhost:9200"
INDEX_NAME = "products_recommend"
BASE_PATH = r"D:\Project\BackEnd\FoodTourApp_BE\Model\RecommendByBehavior\content\model_output"

# ── Mapping ──────────────────────────────────────────────────────────────────
mapping = {
    "mappings": {
        "properties": {
            "product_id":  {"type": "integer"},
            "name":        {"type": "text"},
            "shop_id":     {"type": "integer"},
            "category_id": {"type": "integer"},
            "rating":      {"type": "float"},
            "embedding": {
                "type":       "dense_vector",
                "dims":       384,
                "index":      True,
                "similarity": "cosine"
            }
        }
    }
}

# Xóa index cũ nếu có
r = requests.delete(f"{ES_URL}/{INDEX_NAME}")
print("Delete old index:", r.status_code)

# Tạo index mới
r = requests.put(f"{ES_URL}/{INDEX_NAME}", json=mapping)
print("Create index:", r.json())

# ── Load data ─────────────────────────────────────────────────────────────────
embeddings = np.load(f"{BASE_PATH}/product_embeddings.npy")
df_products = pd.read_csv(f"{BASE_PATH}/products_indexed.csv")

print(f"Products: {len(df_products)} rows")
print(f"Embeddings shape: {embeddings.shape}")
print(f"Columns: {df_products.columns.tolist()}")

# Kiểm tra khớp số lượng
assert len(df_products) == len(embeddings), \
    f"Mismatch! {len(df_products)} products vs {len(embeddings)} embeddings"

# ── Index từng sản phẩm ───────────────────────────────────────────────────────
success = 0
failed  = 0

for row_idx in range(len(df_products)):
    row = df_products.iloc[row_idx]

    doc = {
        "product_id":  int(row['id']),
        "name":        str(row['name']),
        "shop_id":     int(row['shop_id']),
        "category_id": int(row['category_id']),
        "rating":      float(row['rating']),
        "embedding":   embeddings[row_idx].tolist()   # dùng row_idx, không dùng _
    }

    r = requests.post(
        f"{ES_URL}/{INDEX_NAME}/_doc/{int(row['id'])}",
        json=doc
    )

    if r.status_code in (200, 201):
        success += 1
    else:
        failed += 1
        print(f"  ❌ Failed product {int(row['id'])}: {r.text[:100]}")

    if row_idx % 100 == 0:
        print(f"  Progress: {row_idx}/{len(df_products)}")

print(f"\n✅ Done! Success: {success}, Failed: {failed}")

# ── Verify ────────────────────────────────────────────────────────────────────
# Force refresh để ES đếm được ngay
requests.post(f"{ES_URL}/{INDEX_NAME}/_refresh")

r = requests.get(f"{ES_URL}/{INDEX_NAME}/_count")
print(f"✅ ES index count: {r.json().get('count', '?')} documents")

# Test search thử
r = requests.get(f"{ES_URL}/{INDEX_NAME}/_search?size=3")
hits = r.json().get('hits', {}).get('hits', [])
print(f"\n📋 Sample documents in ES:")
for h in hits:
    src = h['_source']
    print(f"  [{src['product_id']}] {src['name']} | shop={src['shop_id']} | rating={src['rating']}")
