import pymysql
from elasticsearch import Elasticsearch, helpers
from datetime import datetime
import json
import requests

# ================== CONFIG ==================
MYSQL_HOST = 'localhost'
MYSQL_PORT = 3307
MYSQL_USER = 'minhngosen196'
MYSQL_PASSWORD = 'minhngosen196'
MYSQL_DB = 'food_tour_app'
ES_HOST = 'http://localhost:9200'
ES_INDEX_PRODUCT = 'foodtour_products_chatbot'

# Ollama embedding (giống Java)
OLLAMA_URL = "http://localhost:11434/api/embeddings"
EMBEDDING_MODEL = "nomic-embed-text"

print("🔥 Đang kết nối Ollama embedding...")

def get_embedding(text):
    try:
        resp = requests.post(OLLAMA_URL, json={"model": EMBEDDING_MODEL, "prompt": text})
        resp.raise_for_status()
        return resp.json()["embedding"]
    except Exception as e:
        print(f"Lỗi embedding: {e}")
        return [0.0] * 768

# Kết nối
def get_mysql_connection():
    return pymysql.connect(
        host=MYSQL_HOST, port=MYSQL_PORT, user=MYSQL_USER,
        password=MYSQL_PASSWORD, database=MYSQL_DB,
        cursorclass=pymysql.cursors.DictCursor
    )

es = Elasticsearch([ES_HOST])

# Mapping cho products (đã thêm context_text)
product_mapping = {
    "properties": {
        "id": {"type": "integer"},
        "shop_id": {"type": "integer"},
        "category_id": {"type": "integer"},
        "name": {"type": "text"},
        "description": {"type": "text"},
        "price": {"type": "scaled_float", "scaling_factor": 100},
        "discount_price": {"type": "scaled_float", "scaling_factor": 100},
        "ingredients": {"type": "text"},
        "nutrition_info": {"type": "text"},
        "preparation_time": {"type": "integer"},
        "is_available": {"type": "boolean"},
        "rating": {"type": "double"},
        "total_reviews": {"type": "long"},
        "tags": {"type": "keyword"},
        "shop_name": {"type": "text"},
        "category_name": {"type": "text"},
        "context_text": {"type": "text"},           # ← Quan trọng cho RAG
        "embedding": {
            "type": "dense_vector",
            "dims": 768,
            "index": True,
            "similarity": "cosine"
        }
    }
}

def create_index_if_not_exists(index_name, mapping):
    if not es.indices.exists(index=index_name):
        es.indices.create(index=index_name, body={"mappings": mapping})
        print(f"✅ Tạo index {index_name}")
    else:
        print(f"✅ Index {index_name} đã tồn tại")

def sync_products():
    conn = get_mysql_connection()
    query = """
        SELECT p.*, s.shop_name, c.name as category_name
        FROM products p
        LEFT JOIN shops s ON p.shop_id = s.id
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.is_available = 1
    """
    with conn.cursor() as cursor:
        cursor.execute(query)
        rows = cursor.fetchall()

    actions = []
    for row in rows:
        # Xử lý boolean & datetime
        for field in ['is_available']:
            if field in row and isinstance(row[field], bytes):
                row[field] = bool(row[field])
        for key, value in row.items():
            if isinstance(value, datetime):
                row[key] = value.isoformat()

        # Tạo context_text theo đúng format system prompt
        price = row.get('discount_price') if row.get('discount_price') else row.get('price', 0)
        context_text = f"""===PRODUCT===
Món: {row.get('name')}
Quán: {row.get('shop_name', 'Không rõ')} (shopId: {row.get('shop_id')})
Danh mục: {row.get('category_name', 'Không rõ')}
Mô tả: {row.get('description', '')}
Nguyên liệu: {row.get('ingredients', '')}
Tags: {row.get('tags', 'không có')}
Dinh dưỡng: {row.get('nutrition_info', 'không có')}
Thời gian chuẩn bị: {row.get('preparation_time', 0)} phút
Giá: {price} VNĐ
Rating: {row.get('rating', 0):.1f}/5.0 ({row.get('total_reviews', 0)} đánh giá)
===END_PRODUCT==="""

        row['context_text'] = context_text

        # Embedding rich text
        embedding = get_embedding(context_text)
        row['embedding'] = embedding

        actions.append({
            "_index": ES_INDEX_PRODUCT,
            "_id": str(row['id']),
            "_source": row
        })

    if actions:
        helpers.bulk(es, actions)
        print(f"✅ Synced {len(actions)} món ăn (có context_text + embedding)")
    else:
        print("Không có món nào để sync")

if __name__ == "__main__":
    create_index_if_not_exists(ES_INDEX_PRODUCT, product_mapping)
    sync_products()
    print("🎉 Sync ES hoàn tất! Chạy file này mỗi khi cần update full data.")