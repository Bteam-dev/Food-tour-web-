import pymysql
from elasticsearch import Elasticsearch, helpers
from datetime import datetime
from decimal import Decimal
import json
import os
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
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY", "AIzaSyAdYztJT2I_rPgKdBRbVOFtKTP3LdK2fd0")
EMBEDDING_MODEL = "gemini-embedding-001"

client = genai.Client(api_key=GEMINI_API_KEY)

print("Connecting to Google Embedding API...")

def get_embedding(text):
    try:
        result = client.models.embed_content(model=EMBEDDING_MODEL, contents=text)
        return result.embeddings[0].values
    except Exception as e:
        print(f"Embedding error: {e}")
        return [0.0] * 3072

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
            "dims": 3072,
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
        # Convert Decimal to float, bytes to bool, datetime to string
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

        # Tạo context_text theo đúng format system prompt
        price = row.get('discount_price') if row.get('discount_price') else row.get('price', 0)
        rating = row.get('rating') or 0
        tags = row.get('tags', '')
        if isinstance(tags, list):
            tags = ', '.join(tags)
        ingredients = row.get('ingredients', '')
        if isinstance(ingredients, list):
            ingredients = ', '.join(ingredients)
        nutrition_info = row.get('nutrition_info', '')
        if isinstance(nutrition_info, list):
            nutrition_info = ', '.join(nutrition_info)

        context_text = f"""===PRODUCT===
Món: {row.get('name')}
Quán: {row.get('shop_name', 'Không rõ')} (shopId: {row.get('shop_id')})
Danh mục: {row.get('category_name', 'Không rõ')}
Mô tả: {row.get('description', '')}
Nguyên liệu: {ingredients}
Tags: {tags or 'không có'}
Dinh dưỡng: {nutrition_info or 'không có'}
Thời gian chuẩn bị: {row.get('preparation_time', 0)} phút
Giá: {price} VNĐ
Rating: {rating:.1f}/5.0 ({row.get('total_reviews', 0)} đánh giá)
===END_PRODUCT==="""

        # Embedding rich text
        embedding = get_embedding(context_text)

        # Chỉ đưa các field có trong ES mapping vào _source
        doc = {
            "id": row.get('id'),
            "shop_id": row.get('shop_id'),
            "category_id": row.get('category_id'),
            "name": row.get('name'),
            "description": row.get('description', ''),
            "price": row.get('price', 0),
            "discount_price": row.get('discount_price'),
            "ingredients": ingredients,
            "nutrition_info": nutrition_info,
            "preparation_time": row.get('preparation_time', 0),
            "is_available": bool(row.get('is_available', True)),
            "rating": rating,
            "total_reviews": row.get('total_reviews', 0),
            "tags": row.get('tags') if isinstance(row.get('tags'), list) else [],
            "shop_name": row.get('shop_name', ''),
            "category_name": row.get('category_name', ''),
            "context_text": context_text,
            "embedding": embedding,
        }

        actions.append({
            "_index": ES_INDEX_PRODUCT,
            "_id": str(row['id']),
            "_source": doc
        })

    if actions:
        success, errors = helpers.bulk(es, actions, raise_on_error=False)
        if errors:
            print(f"❌ {len(errors)} document(s) failed:")
            for err in errors[:5]:
                print(json.dumps(err, indent=2, ensure_ascii=False))
        else:
            print(f"✅ Synced {success} món ăn (có context_text + embedding)")
    else:
        print("Không có món nào để sync")

if __name__ == "__main__":
    # Xóa index cũ và tạo lại để tránh mapping conflict
    if es.indices.exists(index=ES_INDEX_PRODUCT):
        es.indices.delete(index=ES_INDEX_PRODUCT)
        print(f"🗑️ Đã xóa index cũ {ES_INDEX_PRODUCT}")
    create_index_if_not_exists(ES_INDEX_PRODUCT, product_mapping)
    sync_products()
    print("🎉 Sync ES hoàn tất! Chạy file này mỗi khi cần update full data.")