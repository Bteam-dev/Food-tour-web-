"""
Sync Product Embeddings to Elasticsearch for Real-Time Recommendations

Usage:
    1. Train model on Colab, download model_output.zip
    2. Extract to: src/main/resources/models/content/model_output/
    3. Run this script: python sync_es_rcm_behavior.py

This script reads es_documents.json (from Colab) and indexes into Elasticsearch.
"""

import requests
import json
import os
import sys

# ══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ══════════════════════════════════════════════════════════════════════════════

ES_URL = "http://localhost:9200"
INDEX_NAME = "products_recommend"

# Path to model output folder
# Relative to this script location or absolute
SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
BASE_PATH = os.path.join(SCRIPT_DIR, "..", "..", "src", "main", "resources", "models", "FoodRecommendSearchByBehavior", "model_output")

# Alternative: absolute path
# BASE_PATH = r"D:\Project\BackEnd\FoodTourApp_BE\FoodTourApp\src\main\resources\models\content\model_output"

# ══════════════════════════════════════════════════════════════════════════════
# LOAD CONFIG & DATA
# ══════════════════════════════════════════════════════════════════════════════

# Load config to get embedding dimension
config_path = os.path.join(BASE_PATH, "config.json")
es_docs_path = os.path.join(BASE_PATH, "es_documents.json")

# Check files exist
if not os.path.exists(config_path):
    print(f"❌ Config not found: {config_path}")
    print("Please extract model_output.zip to the correct location first.")
    sys.exit(1)

if not os.path.exists(es_docs_path):
    print(f"❌ ES documents not found: {es_docs_path}")
    sys.exit(1)

# Load config
with open(config_path, 'r', encoding='utf-8') as f:
    config = json.load(f)

EMBEDDING_DIM = config.get('embedding_dim', 64)
print(f"📋 Config loaded:")
print(f"   Embedding dim: {EMBEDDING_DIM}")
print(f"   Model trained at: {config.get('trained_at', 'unknown')}")

# Load ES documents
with open(es_docs_path, 'r', encoding='utf-8') as f:
    es_documents = json.load(f)

print(f"   Documents to index: {len(es_documents)}")

# ══════════════════════════════════════════════════════════════════════════════
# CREATE ELASTICSEARCH INDEX
# ══════════════════════════════════════════════════════════════════════════════

mapping = {
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0
    },
    "mappings": {
        "properties": {
            "product_id":  {"type": "integer"},
            "name":        {"type": "text", "analyzer": "standard"},
            "shop_id":     {"type": "integer"},
            "category_id": {"type": "integer"},
            "rating":      {"type": "float"},
            "embedding": {
                "type":       "dense_vector",
                "dims":       EMBEDDING_DIM,
                "index":      True,
                "similarity": "cosine"
            }
        }
    }
}

# Delete old index
print(f"\n🗑️ Deleting old index '{INDEX_NAME}'...")
r = requests.delete(f"{ES_URL}/{INDEX_NAME}")
print(f"   Status: {r.status_code}")

# Create new index
print(f"\n📁 Creating index with embedding dim={EMBEDDING_DIM}...")
r = requests.put(f"{ES_URL}/{INDEX_NAME}", json=mapping)
if r.status_code in (200, 201):
    print(f"   ✅ Index created successfully")
else:
    print(f"   ❌ Failed to create index: {r.text}")
    sys.exit(1)

# ══════════════════════════════════════════════════════════════════════════════
# INDEX DOCUMENTS (BULK API FOR PERFORMANCE)
# ══════════════════════════════════════════════════════════════════════════════

print(f"\n📤 Indexing {len(es_documents)} documents...")

BATCH_SIZE = 100
success = 0
failed = 0

for i in range(0, len(es_documents), BATCH_SIZE):
    batch = es_documents[i:i + BATCH_SIZE]
    
    # Build bulk request body
    bulk_body = ""
    for doc in batch:
        product_id = doc.get('product_id')
        
        # Validate embedding dimension
        embedding = doc.get('embedding', [])
        if len(embedding) != EMBEDDING_DIM:
            print(f"   ⚠️ Product {product_id}: wrong embedding dim {len(embedding)}, expected {EMBEDDING_DIM}")
            failed += 1
            continue
        
        # Prepare document
        clean_doc = {
            "product_id": product_id,
            "name": doc.get('name', f'Product {product_id}'),
            "embedding": embedding
        }
        
        # Add optional fields
        if doc.get('shop_id') is not None:
            clean_doc['shop_id'] = int(doc['shop_id'])
        if doc.get('category_id') is not None:
            clean_doc['category_id'] = int(doc['category_id'])
        if doc.get('rating') is not None:
            clean_doc['rating'] = float(doc['rating'])
        
        # Add to bulk body
        action = json.dumps({"index": {"_index": INDEX_NAME, "_id": str(product_id)}})
        document = json.dumps(clean_doc)
        bulk_body += f"{action}\n{document}\n"
    
    if bulk_body:
        r = requests.post(
            f"{ES_URL}/_bulk",
            data=bulk_body,
            headers={"Content-Type": "application/x-ndjson"}
        )
        
        if r.status_code == 200:
            result = r.json()
            batch_errors = result.get('errors', False)
            if batch_errors:
                for item in result.get('items', []):
                    if 'error' in item.get('index', {}):
                        failed += 1
                        print(f"   ❌ Error: {item['index']['error']}")
                    else:
                        success += 1
            else:
                success += len(batch)
        else:
            failed += len(batch)
            print(f"   ❌ Bulk request failed: {r.status_code}")
    
    # Progress
    if (i + BATCH_SIZE) % 500 == 0 or i + BATCH_SIZE >= len(es_documents):
        print(f"   Progress: {min(i + BATCH_SIZE, len(es_documents))}/{len(es_documents)}")

print(f"\n✅ Indexing complete! Success: {success}, Failed: {failed}")

# ══════════════════════════════════════════════════════════════════════════════
# VERIFY
# ══════════════════════════════════════════════════════════════════════════════

# Force refresh
requests.post(f"{ES_URL}/{INDEX_NAME}/_refresh")

# Count documents
r = requests.get(f"{ES_URL}/{INDEX_NAME}/_count")
count = r.json().get('count', 0)
print(f"\n📊 ES Index Stats:")
print(f"   Total documents: {count}")

# Sample search
r = requests.get(f"{ES_URL}/{INDEX_NAME}/_search?size=3")
hits = r.json().get('hits', {}).get('hits', [])
print(f"\n📋 Sample documents:")
for h in hits:
    src = h['_source']
    print(f"   [{src.get('product_id')}] {src.get('name', 'N/A')[:40]}...")
    print(f"       Embedding dim: {len(src.get('embedding', []))}")

# Test KNN search
print(f"\n🔍 Testing KNN search...")
if hits:
    test_embedding = hits[0]['_source'].get('embedding', [])
    knn_query = {
        "knn": {
            "field": "embedding",
            "query_vector": test_embedding,
            "k": 5,
            "num_candidates": 20
        }
    }
    r = requests.post(f"{ES_URL}/{INDEX_NAME}/_search", json=knn_query)
    if r.status_code == 200:
        knn_hits = r.json().get('hits', {}).get('hits', [])
        print(f"   ✅ KNN search returned {len(knn_hits)} results")
        for h in knn_hits[:3]:
            print(f"      - [{h['_source'].get('product_id')}] score: {h.get('_score', 0):.4f}")
    else:
        print(f"   ❌ KNN search failed: {r.text[:100]}")

print(f"\n🎉 Done! Index '{INDEX_NAME}' is ready for real-time recommendations.")
