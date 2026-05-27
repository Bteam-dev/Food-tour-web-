import pymysql
from elasticsearch import Elasticsearch, helpers
from datetime import datetime
from decimal import Decimal
import json
import os
import time
import re
from google import genai

# ================== CONFIG ==================
MYSQL_HOST = 'localhost'
MYSQL_PORT = 3307
MYSQL_USER = 'minhngosen196'
MYSQL_PASSWORD = 'minhngosen196'
MYSQL_DB = 'food_tour_app'
ES_HOST = 'http://localhost:9200'
ES_INDEX_PRODUCT = 'foodtour_products_chatbot'

# Google Gemini Embedding API (free tier)
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "AIzaSyCcmC0bVY9GYjzkzbnK-YgTBS7gctDvWXg")
EMBEDDING_MODEL = "gemini-embedding-001"

client = genai.Client(api_key=GEMINI_API_KEY)

print("Connecting to Google Embedding API...")

def get_embedding(text):
    attempt = 0
    while True:
        try:
            result = client.models.embed_content(model=EMBEDDING_MODEL, contents=text)
            return result.embeddings[0].values
        except Exception as e:
            err_str = str(e)
            if '429' in err_str or 'RESOURCE_EXHAUSTED' in err_str:
                match = re.search(r"'retryDelay':\s*'(\d+)", err_str)
                wait = int(match.group(1)) + 2 if match else 15
                attempt += 1
                print(f"  [429] Rate limited. Chờ {wait}s rồi retry (lần {attempt})...")
                time.sleep(wait)
            else:
                raise

def get_mysql_connection():
    return pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT, user=MYSQL_USER,
        password=MYSQL_PASSWORD, database=MYSQL_DB,
        cursorclass=pymysql.cursors.DictCursor
    )

es = Elasticsearch([ES_HOST])

# Mapping cho chatbot index — phải khớp với EsChatbotSyncConsumer
product_mapping = {
    "properties": {
        "id":               {"type": "integer"},
        "shop_id":          {"type": "integer"},
        "category_id":      {"type": "integer"},
        "name":             {"type": "text"},
        "description":      {"type": "text"},
        "price":            {"type": "scaled_float", "scaling_factor": 100},
        "discount_price":   {"type": "scaled_float", "scaling_factor": 100},
        "ingredients":      {"type": "text"},
        "nutrition_info":   {"type": "text"},
        "preparation_time": {"type": "integer"},
        "is_available":     {"type": "boolean"},
        "rating":           {"type": "double"},
        "total_reviews":    {"type": "long"},
        "tags":             {"type": "keyword"},
        "shop_name":        {"type": "text"},
        "category_name":    {"type": "text"},
        # Location fields — để chatbot hiểu "quận X có món Y không?"
        "shop_address":     {"type": "text"},
        "shop_ward":        {"type": "keyword"},
        "shop_district":    {"type": "keyword"},
        "shop_city":        {"type": "keyword"},
        "context_text":     {"type": "text"},           # Cho RAG
        "embedding": {
            "type": "dense_vector",
            "dims": 3072,
            "index": True,
            "similarity": "cosine"
        }
    }
}

def create_index(index_name, mapping):
    es.indices.create(index=index_name, body={"mappings": mapping})
    print(f"✅ Tạo index {index_name}")

def sync_products():
    conn = get_mysql_connection()
    query = """
        SELECT
            p.*,
            s.shop_name,
            s.address_line  AS shop_address_line,
            s.ward          AS shop_ward,
            s.district      AS shop_district,
            s.city          AS shop_city,
            c.name          AS category_name
        FROM products p
        LEFT JOIN shops s ON p.shop_id = s.id
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.is_available = 1
    """
    with conn.cursor() as cursor:
        cursor.execute(query)
        rows = cursor.fetchall()
    conn.close()
    print(f"📦 Fetched {len(rows)} products from MySQL")

    actions = []
    for i, row in enumerate(rows, 1):
        # Convert types
        for key, value in row.items():
            if isinstance(value, Decimal):
                row[key] = float(value)
            elif isinstance(value, bytes):
                row[key] = bool(value)
            elif isinstance(value, datetime):
                row[key] = value.isoformat()

        # Parse JSON string fields
        for field in ['tags', 'ingredients', 'nutrition_info']:
            val = row.get(field)
            if val and isinstance(val, str):
                try:
                    row[field] = json.loads(val)
                except json.JSONDecodeError:
                    pass

        # Flatten list fields thành string
        tags = row.get('tags', '')
        if isinstance(tags, list):
            tags = ', '.join(tags)
        ingredients = row.get('ingredients', '')
        if isinstance(ingredients, list):
            ingredients = ', '.join(ingredients)
        nutrition_info = row.get('nutrition_info', '')
        if isinstance(nutrition_info, list):
            nutrition_info = ', '.join(nutrition_info)

        # Build địa chỉ đầy đủ (giống Java buildContextText)
        parts = []
        if row.get('shop_address_line'):
            parts.append(row['shop_address_line'])
        if row.get('shop_ward'):
            parts.append(row['shop_ward'])
        if row.get('shop_district'):
            parts.append(row['shop_district'])
        if row.get('shop_city'):
            parts.append(row['shop_city'])
        full_address = ', '.join(parts) if parts else 'Không rõ'

        district = row.get('shop_district') or ''
        city     = row.get('shop_city') or ''

        # Price
        price = row.get('discount_price') if row.get('discount_price') else row.get('price', 0)
        rating = row.get('rating') or 0.0

        # context_text — phải khớp với EsChatbotSyncConsumer.buildContextText()
        context_text = f"""===PRODUCT===
Món: {row.get('name')}
Quán: {row.get('shop_name', 'Không rõ')} (shopId: {row.get('shop_id')})
Danh mục: {row.get('category_name', 'Không rõ')}
Địa chỉ quán: {full_address}
Quận/Huyện: {district}
Thành phố: {city}
Mô tả: {row.get('description', '')}
Nguyên liệu: {ingredients}
Tags: {tags or 'không có'}
Dinh dưỡng: {nutrition_info or 'không có'}
Thời gian chuẩn bị: {row.get('preparation_time', 0)} phút
Giá: {price} VNĐ
Rating: {rating:.1f}/5.0 ({row.get('total_reviews', 0)} đánh giá)
===END_PRODUCT==="""

        print(f"  [{i}/{len(rows)}] Embedding: {row.get('name')} ...")
        embedding = get_embedding(context_text)
        time.sleep(0.7)  # ~85 req/min, safe dưới giới hạn 100/min

        doc = {
            "id":               row.get('id'),
            "shop_id":          row.get('shop_id'),
            "category_id":      row.get('category_id'),
            "name":             row.get('name'),
            "description":      row.get('description', ''),
            "price":            row.get('price', 0),
            "discount_price":   row.get('discount_price'),
            "ingredients":      ingredients,
            "nutrition_info":   nutrition_info,
            "preparation_time": row.get('preparation_time', 0),
            "is_available":     bool(row.get('is_available', True)),
            "rating":           rating,
            "total_reviews":    row.get('total_reviews', 0),
            "tags":             row.get('tags') if isinstance(row.get('tags'), list) else [],
            "shop_name":        row.get('shop_name', ''),
            "category_name":    row.get('category_name', ''),
            # Location fields
            "shop_address":     full_address,
            "shop_ward":        row.get('shop_ward') or '',
            "shop_district":    district,
            "shop_city":        city,
            "context_text":     context_text,
            "embedding":        embedding,
        }

        actions.append({
            "_index": ES_INDEX_PRODUCT,
            "_id":    str(row['id']),
            "_source": doc
        })

    if actions:
        success, errors = helpers.bulk(es, actions, raise_on_error=False)
        if errors:
            print(f"❌ {len(errors)} document(s) failed:")
            for err in errors[:5]:
                print(json.dumps(err, indent=2, ensure_ascii=False))
        else:
            print(f"\n✅ Indexed {success} sản phẩm thành công!")
    else:
        print("Không có món nào để sync")

if __name__ == "__main__":
    print(f"🗑️  Xóa index cũ '{ES_INDEX_PRODUCT}' nếu tồn tại...")
    if es.indices.exists(index=ES_INDEX_PRODUCT):
        es.indices.delete(index=ES_INDEX_PRODUCT)
        print(f"   Đã xóa.")

    print(f"📁 Tạo index mới với mapping (dense_vector 3072 dims + location fields)...")
    create_index(ES_INDEX_PRODUCT, product_mapping)

    print(f"\n📤 Bắt đầu sync products (mỗi product cần 1 Gemini API call)...")
    sync_products()

    print(f"\n🎉 Sync hoàn tất! Chatbot đã sẵn sàng với dữ liệu mới.")
    print(f"   → Dùng script này mỗi khi cần full reindex (đổi mapping, đổi model, fix data).")
    print(f"   → Cập nhật thường ngày: tự động qua Redis queue (EsChatbotSyncConsumer).")
