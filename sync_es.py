import pymysql
from elasticsearch import Elasticsearch, helpers
from datetime import datetime
import json
from sentence_transformers import SentenceTransformer

# ================== CONFIG ==================
MYSQL_HOST = 'localhost'
MYSQL_PORT = 3307
MYSQL_USER = 'minhngosen196'
MYSQL_PASSWORD = 'minhngosen196'
MYSQL_DB = 'food_tour_app'

ES_HOST = 'http://localhost:9200'
ES_INDEX_PRODUCT = 'foodtour_products'
ES_INDEX_REVIEWS = 'foodtour_reviews'
ES_INDEX_SHOPS = 'foodtour_shops'
ES_INDEX_CATEGORIES = 'foodtour_categories'

# Model embedding (nhỏ, nhanh, 384 dims)
model = SentenceTransformer('all-MiniLM-L6-v2')
print("Model embedding đã tải xong.")

# Kết nối MySQL
def get_mysql_connection():
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DB,
        cursorclass=pymysql.cursors.DictCursor
    )

# Kết nối ES
es = Elasticsearch([ES_HOST])

# Tạo index nếu chưa tồn tại
def create_index_if_not_exists(index_name, mapping):
    if not es.indices.exists(index=index_name):
        es.indices.create(index=index_name, body={"mappings": mapping})
        print(f"Tạo index {index_name} thành công.")
    else:
        print(f"Index {index_name} đã tồn tại.")

# Mapping cho từng index
product_mapping = {
    "properties": {
        "id": {"type": "integer"},
        "shop_id": {"type": "integer"},
        "category_id": {"type": "integer"},
        "name": {"type": "text"},
        "description": {"type": "text"},
        "price": {"type": "scaled_float", "scaling_factor": 100},
        "discount_price": {"type": "scaled_float", "scaling_factor": 100},
        "image_urls": {"type": "text"},
        "ingredients": {"type": "text"},
        "nutrition_info": {"type": "text"},
        "preparation_time": {"type": "integer"},
        "is_available": {"type": "boolean"},
        "stock_quantity": {"type": "integer"},
        "min_order_quantity": {"type": "integer"},
        "max_order_quantity": {"type": "integer"},
        "rating": {"type": "double"},
        "total_reviews": {"type": "long"},
        "tags": {"type": "keyword"},
        "created_at": {"type": "date"},
        "updated_at": {"type": "date"},
        # THÊM FIELD VECTOR
        "embedding": {
            "type": "dense_vector",
            "dims": 384,
            "index": True,
            "similarity": "cosine"
        }
    }
}

# Các mapping khác giữ nguyên (không cần embedding cho reviews/shops/categories)
review_mapping = { ... }  # copy nguyên từ code cũ của mày
shop_mapping = { ... }    # copy nguyên
category_mapping = { ... }  # copy nguyên

# Sync data cho từng table
def sync_data(table_name, index_name, query, add_embedding=False):
    conn = get_mysql_connection()
    with conn.cursor() as cursor:
        cursor.execute(query)
        rows = cursor.fetchall()
    
    actions = []
    for row in rows:
        # Xử lý boolean từ bytes
        boolean_fields = ['is_available', 'is_verified', 'is_active', 'is_anonymous', 'is_approved', 'has_refund_request']
        for field in boolean_fields:
            if field in row and isinstance(row[field], bytes):
                row[field] = bool(row[field])
        
        # Convert datetime sang ISO
        for key, value in row.items():
            if isinstance(value, datetime):
                row[key] = value.isoformat()
            elif key == 'opening_hours' and value:
                try:
                    row[key] = json.loads(value)
                except:
                    row[key] = {}
        
        # THÊM EMBEDDING NẾU CẦN (chỉ cho products)
        if add_embedding:
            text = f"{row.get('name', '')} {row.get('description', '') or ''}"
            if 'shop_name' in row:
                text += f" Quán: {row['shop_name']}"
            try:
                embedding = model.encode(text).tolist()
                row['embedding'] = embedding
            except Exception as e:
                print(f"Lỗi embedding cho row {row.get('id')}: {e}")
                row['embedding'] = [0.0] * 384  # fallback nếu lỗi
        
        actions.append({
            "_index": index_name,
            "_id": row['id'],
            "_source": row
        })
    
    if actions:
        helpers.bulk(es, actions)
        print(f"Synced {len(actions)} documents to {index_name}")
    else:
        print(f"Không có data để sync cho {index_name}")

# Main
if __name__ == "__main__":
    # Tạo indexes
    create_index_if_not_exists(ES_INDEX_PRODUCT, product_mapping)
    create_index_if_not_exists(ES_INDEX_REVIEWS, review_mapping)
    create_index_if_not_exists(ES_INDEX_SHOPS, shop_mapping)
    create_index_if_not_exists(ES_INDEX_CATEGORIES, category_mapping)
    
    # Sync với embedding cho products
    sync_data('products', ES_INDEX_PRODUCT, "SELECT * FROM products", add_embedding=True)
    
    # Sync bình thường cho các bảng khác
    sync_data('reviews', ES_INDEX_REVIEWS, "SELECT * FROM reviews")
    sync_data('shops', ES_INDEX_SHOPS, "SELECT * FROM shops")
    sync_data('categories', ES_INDEX_CATEGORIES, "SELECT * FROM categories")
    
    print("Sync completed!")