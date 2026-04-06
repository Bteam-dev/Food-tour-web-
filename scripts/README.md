# Python Scripts - Full Reindex Only

## ⚠️ IMPORTANT: When to Use These Scripts

### ❌ **DO NOT USE for daily operations**
- Incremental sync (Product create/update/delete) is handled by **Java background consumers**
- See `REDIS_ES_SYNC_ARCHITECTURE.md` for details

### ✅ **USE ONLY for Full Reindex scenarios:**
1. **First time setup** - Empty ES indexes, need to sync all existing products
2. **ES data deleted** - Accidentally deleted index or data corruption
3. **Mapping changes** - Changed ES field types, analyzers, or added new fields
4. **Migration** - Moving to new ES server

---

## Scripts Overview

### 1. Chatbot Index (`scripts/chatbot/sync_es_chatbot.py`)
**Purpose**: Full reindex ALL products to `foodtour_products_chatbot` with embeddings

**What it does:**
- Fetches ALL products from MySQL (với Shop và Category thông tin)
- Generates 768-dim embeddings via Ollama
- Creates structured `context_text` for RAG
- Bulk indexes to ES with dense vectors

**When to run:**
- First deployment (empty chatbot index)
- Changed embedding model (nomic-embed-text → other model)
- Changed context_text format
- ES mapping updated (added new fields)

**Run:**
```bash
cd scripts/chatbot
python sync_es_chatbot.py
```

**Expected time:** ~2-5 minutes for 1000 products (embedding generation is slow)

---

### 2. Search Index (`scripts/search/sync_es_search.py`)
**Purpose**: Full reindex ALL products to `foodtour_products_search` (no embeddings)

**What it does:**
- Fetches ALL products from MySQL
- Direct bulk indexing (FAST - no embeddings)
- Full-text search optimized fields

**When to run:**
- First deployment (empty search index)
- Changed search mapping (analyzers, fields)
- Data corruption in search index

**Run:**
```bash
cd scripts/search
python sync_es_search.py
```

**Expected time:** ~10-30 seconds for 1000 products (no embedding overhead)

---

### 3. Recommendation Index (`scripts/recommend/sync_es_rcm_behavior.py`)
**Purpose**: Sync user behavior data for recommendation system

**Status:** Independent system - not affected by product sync changes

---

## Prerequisites

### 1. Python Environment
```bash
# Create virtual environment (first time only)
python -m venv venv

# Activate (Windows)
venv\Scripts\activate

# Activate (Linux/Mac)
source venv/bin/activate

# Install dependencies
pip install pymysql elasticsearch requests
```

### 2. Running Services
- **MySQL** on port 3307
- **Elasticsearch** on port 9200
- **Ollama** on port 11434 (for chatbot script only)

### 3. Environment Check
```bash
# MySQL
mysql -h localhost -P 3307 -u root -p -e "SELECT COUNT(*) FROM foodtour.product;"

# Elasticsearch
curl http://localhost:9200

# Ollama (chatbot only)
curl http://localhost:11434/api/tags
```

---

## Usage Examples

### Scenario 1: First Time Setup (Empty ES)
```bash
# Step 1: Start services
docker-compose up -d mysql elasticsearch redis

# Step 2: Start Ollama
ollama serve

# Step 3: Run full reindex (both indexes)
cd scripts/chatbot
python sync_es_chatbot.py

cd ../search
python sync_es_search.py

# Step 4: Verify data
curl http://localhost:9200/foodtour_products_chatbot/_count
curl http://localhost:9200/foodtour_products_search/_count
```

### Scenario 2: ES Data Corrupted (Reindex one index)
```bash
# Option A: Reindex chatbot only
cd scripts/chatbot
python sync_es_chatbot.py

# Option B: Reindex search only
cd scripts/search
python sync_es_search.py
```

### Scenario 3: Changed ES Mapping
```bash
# Step 1: Delete old index
curl -X DELETE http://localhost:9200/foodtour_products_chatbot

# Step 2: Create new mapping (from your ES config)
curl -X PUT http://localhost:9200/foodtour_products_chatbot -H 'Content-Type: application/json' -d @mapping.json

# Step 3: Full reindex
cd scripts/chatbot
python sync_es_chatbot.py
```

---

## Monitoring Progress

### Check Script Output
```
Fetching products from MySQL...
Found 1523 products
Processing product 100/1523...
Processing product 200/1523...
...
✓ Successfully synced 1523 products to Elasticsearch
```

### Check ES Index Count
```bash
# Chatbot index
curl http://localhost:9200/foodtour_products_chatbot/_count

# Search index
curl http://localhost:9200/foodtour_products_search/_count
```

### Check Sample Document
```bash
# Get product by ID
curl http://localhost:9200/foodtour_products_chatbot/_doc/123

# Verify embedding exists (chatbot only)
curl http://localhost:9200/foodtour_products_chatbot/_doc/123 | jq '.["_source"]["embedding"]'
```

---

## Troubleshooting

### Script fails: "Can't connect to MySQL"
```bash
# Check MySQL is running
docker-compose ps mysql

# Test connection
mysql -h localhost -P 3307 -u root -p
```

### Script fails: "Elasticsearch connection error"
```bash
# Check ES is running
curl http://localhost:9200/_cluster/health

# Check ES logs
docker-compose logs elasticsearch
```

### Chatbot script fails: "Ollama connection error"
```bash
# Check Ollama is running
curl http://localhost:11434/api/tags

# Start Ollama
ollama serve

# Pull embedding model
ollama pull nomic-embed-text
```

### Script slow / timeout
```python
# Edit script: Reduce batch size
BATCH_SIZE = 50  # Change to 20 or 10
```

---

## After Full Reindex

### ✅ Normal operations resume:
- Java consumers handle incremental sync (Product create/update/delete)
- No need to run scripts again UNLESS:
  - ES data deleted
  - Mapping changed
  - Migration

### ✅ Verify incremental sync works:
```bash
# 1. Create a new Product via API
# 2. Check Redis queue
redis-cli LLEN es:chatbot:sync:queue

# 3. Wait 5 seconds (consumer polls)
# 4. Verify in ES
curl http://localhost:9200/foodtour_products_chatbot/_doc/{new_product_id}
```

---

## Summary

| Operation | Tool | When |
|-----------|------|------|
| **Incremental sync** (daily) | Java consumers | Always (automatic) |
| **Full reindex** (rare) | Python scripts | ES empty/corrupted/mapping changed |

**Remember:** These scripts are **BACKUP TOOLS** for full reindex only. Normal operations use Java consumers!
