#!/usr/bin/env python3
"""
sync_es_search.py - Sync products to Elasticsearch for HYBRID search

Index: foodtour_products_search
Features:
  - BM25 full-text search (Vietnamese analyzer, autocomplete)
  - PhoBERT 768-dim embeddings for semantic search (generated on Colab)
  - Hybrid scoring: BM25 + cosine similarity

Usage:
    python sync_es_search.py --full                    # Full sync (text only)
    python sync_es_search.py --full --embeddings embeddings.csv  # Full sync + embeddings
    python sync_es_search.py --product-id 123          # Sync single product
    python sync_es_search.py --delete-product-id 123   # Delete single product
    python sync_es_search.py --update-sales            # Update sales statistics
    python sync_es_search.py --recreate-index          # Recreate index from scratch
    python sync_es_search.py --load-embeddings embeddings.csv  # Load embeddings only (no resync)

Author: FoodTourApp Team
"""

import argparse
import csv
import json
import re
import sys
import unicodedata
from datetime import datetime
from typing import Optional, List, Dict, Any

import pymysql
from elasticsearch import Elasticsearch, helpers
from elasticsearch.exceptions import NotFoundError

# ================== CONFIG ==================
MYSQL_CONFIG = {
    'host': 'localhost',
    'port': 3307,
    'user': 'minhngosen196',
    'password': 'minhngosen196',
    'database': 'food_tour_app',
    'charset': 'utf8mb4',
    'cursorclass': pymysql.cursors.DictCursor
}

ES_HOST = 'http://localhost:9200'
ES_INDEX = 'foodtour_products_search'
EMBEDDING_DIM = 768  # PhoBERT output dimension

# ================== ELASTICSEARCH MAPPING ==================
INDEX_SETTINGS = {
    "settings": {
        "number_of_shards": 1,
        "number_of_replicas": 0,
        "analysis": {
            "analyzer": {
                "vietnamese_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase", "asciifolding", "vietnamese_stop"]
                },
                "autocomplete_analyzer": {
                    "type": "custom",
                    "tokenizer": "autocomplete_tokenizer",
                    "filter": ["lowercase", "asciifolding"]
                },
                "autocomplete_search": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": ["lowercase", "asciifolding"]
                }
            },
            "tokenizer": {
                "autocomplete_tokenizer": {
                    "type": "edge_ngram",
                    "min_gram": 2,
                    "max_gram": 20,
                    "token_chars": ["letter", "digit"]
                }
            },
            "filter": {
                "vietnamese_stop": {
                    "type": "stop",
                    "stopwords": ["và", "của", "là", "có", "được", "trong", "với", "này", "cho", "các"]
                }
            }
        }
    },
    "mappings": {
        "properties": {
            # IDs
            "id": {"type": "integer"},
            "shop_id": {"type": "integer"},
            "category_id": {"type": "integer"},

            # Shop info
            "shop_name": {
                "type": "text",
                "analyzer": "vietnamese_analyzer",
                "fields": {"keyword": {"type": "keyword"}}
            },
            "shop_city": {
                "type": "keyword",
                "normalizer": "lowercase"
            },
            "shop_district": {
                "type": "keyword",
                "normalizer": "lowercase"
            },
            "shop_address": {"type": "text", "analyzer": "vietnamese_analyzer"},
            "shop_is_verified": {"type": "boolean"},
            "shop_is_active": {"type": "boolean"},
            "opening_hours": {"type": "keyword", "index": False},

            # Category
            "category_name": {
                "type": "text",
                "analyzer": "vietnamese_analyzer",
                "fields": {"keyword": {"type": "keyword"}}
            },

            # Product main fields
            "name": {
                "type": "text",
                "analyzer": "vietnamese_analyzer",
                "fields": {
                    "keyword": {"type": "keyword"},
                    "autocomplete": {
                        "type": "text",
                        "analyzer": "autocomplete_analyzer",
                        "search_analyzer": "autocomplete_search"
                    }
                }
            },
            "name_normalized": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },
            "description": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },
            "description_normalized": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },

            # Price fields
            "price": {"type": "scaled_float", "scaling_factor": 100},
            "discount_price": {"type": "scaled_float", "scaling_factor": 100},
            "effective_price": {"type": "scaled_float", "scaling_factor": 100},
            "has_discount": {"type": "boolean"},
            "discount_percentage": {"type": "float"},

            # Arrays
            "image_urls": {"type": "keyword"},
            "ingredients": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },
            "ingredients_list": {"type": "keyword"},
            "nutrition_info": {"type": "keyword"},
            "tags": {"type": "keyword", "normalizer": "lowercase"},

            # Numeric fields
            "preparation_time": {"type": "integer"},
            "stock_quantity": {"type": "integer"},
            "min_order_quantity": {"type": "integer"},
            "max_order_quantity": {"type": "integer"},

            # Status
            "is_available": {"type": "boolean"},
            # Trạng thái mở/đóng cửa của shop - được cập nhật tự động bởi Spring Scheduler mỗi 60s
            "shop_is_open": {"type": "boolean"},

            # Rating & reviews
            "rating": {"type": "float"},
            "total_reviews": {"type": "long"},
            "rating_score": {"type": "float"},

            # Sales statistics
            "total_sold": {"type": "long"},

            # Timestamps
            "created_at": {"type": "date"},
            "updated_at": {"type": "date"},

            # Combined search field
            "search_text": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },

            # ═══════════ PhoBERT EMBEDDING (trained on Colab) ═══════════
            "embedding": {
                "type": "dense_vector",
                "dims": EMBEDDING_DIM,
                "index": True,
                "similarity": "cosine"
            }
        }
    }
}


def get_mysql_connection():
    return pymysql.connect(**MYSQL_CONFIG)


def get_es_client() -> Elasticsearch:
    return Elasticsearch([ES_HOST])


def normalize_vietnamese(text: str) -> str:
    if not text:
        return ''
    text = text.replace('Đ', 'D').replace('đ', 'd')
    normalized = unicodedata.normalize('NFD', text)
    normalized = re.sub(r'[\u0300-\u036f]', '', normalized)
    return normalized.lower().strip()


def parse_json_array(json_str: str) -> List[str]:
    if not json_str:
        return []
    try:
        result = json.loads(json_str)
        return result if isinstance(result, list) else []
    except (json.JSONDecodeError, TypeError):
        return []


def is_shop_currently_open(opening_hours_json: str) -> bool:
    """Tính trạng thái mở/đóng cửa của shop tại thời điểm sync.
    Format opening_hours: {"monday": {"open": "08:00", "close": "22:00"}, ...}
    """
    if not opening_hours_json:
        return True
    try:
        from datetime import time as dt_time
        hours = json.loads(opening_hours_json)
        now = datetime.now()
        day_key = now.strftime('%A').lower()  # 'monday', 'tuesday', ...
        today_hours = hours.get(day_key)
        if not today_hours:
            return False
        open_str = today_hours.get('open')
        close_str = today_hours.get('close')
        if not open_str or not close_str:
            return False
        open_time = dt_time.fromisoformat(open_str)
        close_time = dt_time.fromisoformat(close_str)
        current_time = now.time().replace(second=0, microsecond=0)
        if close_time < open_time:  # qua đêm (22:00 - 02:00)
            return current_time >= open_time or current_time < close_time
        return open_time <= current_time < close_time
    except Exception:
        return True


def calculate_effective_price(price: float, discount_price: Optional[float]) -> float:
    if discount_price and discount_price > 0 and discount_price < price:
        return discount_price
    return price


def calculate_discount_percentage(price: float, discount_price: Optional[float]) -> float:
    if not discount_price or discount_price <= 0 or discount_price >= price:
        return 0.0
    return round(((price - discount_price) / price) * 100, 2)


def calculate_rating_score(rating: float, total_reviews: int) -> float:
    if not rating or not total_reviews:
        return 0.0
    import math
    return round(rating * math.log(total_reviews + 1), 4)


def build_search_document(row: Dict[str, Any], embedding: Optional[List[float]] = None) -> Dict[str, Any]:
    """Build ES document from MySQL row, optionally with PhoBERT embedding"""

    image_urls = parse_json_array(row.get('image_urls'))
    ingredients = parse_json_array(row.get('ingredients'))
    nutrition_info = parse_json_array(row.get('nutrition_info'))
    tags = parse_json_array(row.get('tags'))

    price = float(row.get('price') or 0)
    discount_price = float(row.get('discount_price') or 0) if row.get('discount_price') else None
    effective_price = calculate_effective_price(price, discount_price)
    has_discount = discount_price is not None and discount_price > 0 and discount_price < price
    discount_percentage = calculate_discount_percentage(price, discount_price)

    rating = float(row.get('rating') or 0)
    total_reviews = int(row.get('total_reviews') or 0)
    rating_score = calculate_rating_score(rating, total_reviews)

    search_parts = [
        row.get('name', ''),
        row.get('category_name', ''),
        ' '.join(ingredients),
        ' '.join(tags)
    ]
    search_text = ' '.join(filter(None, search_parts))

    name_normalized = normalize_vietnamese(row.get('name', ''))
    description_normalized = normalize_vietnamese(row.get('description', ''))

    created_at = row.get('created_at')
    updated_at = row.get('updated_at')
    if isinstance(created_at, datetime):
        created_at = created_at.isoformat()
    if isinstance(updated_at, datetime):
        updated_at = updated_at.isoformat()

    doc = {
        'id': row['id'],
        'shop_id': row.get('shop_id'),
        'category_id': row.get('category_id'),
        'shop_name': row.get('shop_name'),
        'shop_city': row.get('shop_city', '').lower() if row.get('shop_city') else None,
        'shop_district': row.get('shop_district', '').lower() if row.get('shop_district') else None,
        'shop_address': row.get('shop_address'),
        'opening_hours': row.get('opening_hours'),
        'shop_is_verified': bool(row.get('shop_is_verified')),
        'shop_is_active': bool(row.get('shop_is_active')),
        'category_name': row.get('category_name'),
        'name': row.get('name'),
        'name_normalized': name_normalized,
        'description': row.get('description'),
        'description_normalized': description_normalized,
        'price': price,
        'discount_price': discount_price,
        'effective_price': effective_price,
        'has_discount': has_discount,
        'discount_percentage': discount_percentage,
        'image_urls': image_urls,
        'ingredients': ' '.join(ingredients),
        'ingredients_list': ingredients,
        'nutrition_info': nutrition_info,
        'tags': [t.lower() for t in tags],
        'preparation_time': row.get('preparation_time'),
        'stock_quantity': row.get('stock_quantity', 0),
        'min_order_quantity': row.get('min_order_quantity', 1),
        'max_order_quantity': row.get('max_order_quantity', 999),
        'is_available': bool(row.get('is_available')),
        'shop_is_open': is_shop_currently_open(row.get('opening_hours')),
        'rating': rating,
        'total_reviews': total_reviews,
        'rating_score': rating_score,
        'total_sold': int(row.get('total_sold') or 0),
        'created_at': created_at,
        'updated_at': updated_at,
        'search_text': search_text,
    }

    # PhoBERT embedding (from Colab CSV)
    if embedding and len(embedding) == EMBEDDING_DIM:
        doc['embedding'] = embedding

    return doc


def load_embeddings_from_csv(csv_path: str) -> Dict[int, List[float]]:
    """Load PhoBERT embeddings from CSV exported by Colab"""
    embeddings = {}
    try:
        with open(csv_path, 'r', encoding='utf-8-sig') as f:
            reader = csv.DictReader(f)
            for row in reader:
                pid = int(row['product_id'])
                emb = json.loads(row['embedding'])
                if len(emb) == EMBEDDING_DIM:
                    embeddings[pid] = emb
                else:
                    print(f"  [WARN] Product {pid}: embedding dim={len(emb)}, expected {EMBEDDING_DIM}")
        print(f"[OK] Loaded {len(embeddings)} embeddings from {csv_path}")
    except FileNotFoundError:
        print(f"[WARN] Embeddings file not found: {csv_path}")
    except Exception as e:
        print(f"[ERROR] Failed to load embeddings: {e}")
    return embeddings


def create_index_if_not_exists(es: Elasticsearch):
    if not es.indices.exists(index=ES_INDEX):
        es.indices.create(index=ES_INDEX, body=INDEX_SETTINGS)
        print(f"Created index: {ES_INDEX}")
    else:
        print(f"Index {ES_INDEX} already exists")


def recreate_index(es: Elasticsearch):
    if es.indices.exists(index=ES_INDEX):
        es.indices.delete(index=ES_INDEX)
        print(f"Deleted index: {ES_INDEX}")
    es.indices.create(index=ES_INDEX, body=INDEX_SETTINGS)
    print(f"[OK] Created index: {ES_INDEX}")


def get_products_query(product_id: Optional[int] = None) -> str:
    base_query = """
        SELECT
            p.id, p.shop_id, p.category_id, p.name, p.description,
            p.price, p.discount_price, p.image_urls, p.ingredients,
            p.nutrition_info, p.tags, p.preparation_time, p.stock_quantity,
            p.min_order_quantity, p.max_order_quantity, p.is_available,
            p.rating, p.total_reviews, p.created_at, p.updated_at,
            s.shop_name,
            s.city as shop_city,
            s.district as shop_district,
            s.address_line as shop_address,
            s.opening_hours,
            s.is_verified as shop_is_verified, s.is_active as shop_is_active,
            c.name as category_name,
            COALESCE(
                (SELECT SUM(oi.quantity)
                 FROM order_items oi
                 JOIN orders o ON oi.order_id = o.id
                 WHERE oi.product_id = p.id
                 AND o.order_status = 'delivered'),
                0
            ) as total_sold
        FROM products p
        LEFT JOIN shops s ON p.shop_id = s.id
        LEFT JOIN categories c ON p.category_id = c.id
        WHERE p.is_available = 1
          AND s.is_verified = 1
          AND s.is_active = 1
    """
    if product_id:
        return base_query + f" AND p.id = {product_id}"
    return base_query + " ORDER BY p.created_at DESC"


def sync_full(es: Elasticsearch, embeddings: Optional[Dict[int, List[float]]] = None):
    """Full sync all products, optionally with PhoBERT embeddings"""
    print("[PROCESSING] Starting full sync...")

    conn = get_mysql_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(get_products_query())
            rows = cursor.fetchall()

        if not rows:
            print("No products found to sync")
            return

        emb_count = 0
        actions = []
        for row in rows:
            pid = row['id']
            emb = embeddings.get(pid) if embeddings else None
            doc = build_search_document(row, emb)
            if emb:
                emb_count += 1
            actions.append({
                "_index": ES_INDEX,
                "_id": str(pid),
                "_source": doc
            })

        success, failed = helpers.bulk(es, actions, raise_on_error=False)

        print(f"[OK] Synced {success} products ({emb_count} with embeddings)")
        if failed:
            print(f"[WARN] Failed: {len(failed)} products")
            for item in failed[:5]:
                print(f"   - {item}")

        es.indices.refresh(index=ES_INDEX)

    finally:
        conn.close()


def sync_product(es: Elasticsearch, product_id: int):
    """Sync a single product by ID (no embedding - use load-embeddings for that)"""
    print(f"[PROCESSING] Syncing product ID: {product_id}")

    conn = get_mysql_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(get_products_query(product_id))
            row = cursor.fetchone()

        if not row:
            print(f"Product {product_id} not found or not available, removing from index")
            delete_product(es, product_id)
            return

        # Try to preserve existing embedding
        existing_emb = None
        try:
            existing = es.get(index=ES_INDEX, id=str(product_id), _source_includes=['embedding'])
            if existing['_source'] and 'embedding' in existing['_source']:
                existing_emb = existing['_source']['embedding']
        except NotFoundError:
            pass

        doc = build_search_document(row, existing_emb)
        es.index(index=ES_INDEX, id=str(product_id), document=doc)

        print(f"[OK] Synced product: {row['name']} (ID: {product_id})"
              f"{' [with embedding]' if existing_emb else ''}")

    finally:
        conn.close()


def delete_product(es: Elasticsearch, product_id: int):
    try:
        es.delete(index=ES_INDEX, id=str(product_id))
        print(f"[OK] Deleted product ID: {product_id}")
    except NotFoundError:
        print(f"[INFO] Product {product_id} not found in index")


def load_embeddings_to_index(es: Elasticsearch, csv_path: str):
    """Load PhoBERT embeddings from Colab CSV and update existing docs"""
    embeddings = load_embeddings_from_csv(csv_path)
    if not embeddings:
        return

    print(f"[PROCESSING] Updating {len(embeddings)} products with embeddings...")

    actions = []
    for pid, emb in embeddings.items():
        actions.append({
            "_op_type": "update",
            "_index": ES_INDEX,
            "_id": str(pid),
            "doc": {"embedding": emb}
        })

    if actions:
        success, failed = helpers.bulk(es, actions, raise_on_error=False)
        print(f"[OK] Updated embeddings for {success} products")
        if failed:
            print(f"[WARN] Failed: {len(failed)}")
            for item in failed[:5]:
                print(f"   - {item}")


def update_sales_statistics(es: Elasticsearch):
    print("[PROCESSING] Updating sales statistics...")

    conn = get_mysql_connection()
    try:
        query = """
            SELECT p.id,
                COALESCE(SUM(oi.quantity), 0) as total_sold
            FROM products p
            LEFT JOIN order_items oi ON oi.product_id = p.id
            LEFT JOIN orders o ON oi.order_id = o.id AND o.order_status = 'delivered'
            WHERE p.is_available = 1
            GROUP BY p.id
        """
        with conn.cursor() as cursor:
            cursor.execute(query)
            rows = cursor.fetchall()

        actions = []
        for row in rows:
            actions.append({
                "_op_type": "update",
                "_index": ES_INDEX,
                "_id": str(row['id']),
                "doc": {"total_sold": int(row['total_sold'])}
            })

        if actions:
            success, failed = helpers.bulk(es, actions, raise_on_error=False)
            print(f"[OK] Updated sales for {success} products")
            if failed:
                print(f"[WARN] Failed: {len(failed)}")
    finally:
        conn.close()


def verify_index(es: Elasticsearch):
    try:
        count = es.count(index=ES_INDEX)['count']
        print(f"\n[STATS] Index Statistics:")
        print(f"   Total documents: {count}")

        # Check how many have embeddings
        emb_count = es.count(index=ES_INDEX, body={
            "query": {"exists": {"field": "embedding"}}
        })['count']
        print(f"   With embeddings: {emb_count}")

        result = es.search(
            index=ES_INDEX,
            body={
                "size": 3,
                "query": {"match_all": {}},
                "_source": ["id", "name", "shop_name", "effective_price", "rating", "total_sold"]
            }
        )

        print(f"\n[INFO] Sample documents:")
        for hit in result['hits']['hits']:
            src = hit['_source']
            print(f"   [{src['id']}] {src['name']}")
            print(f"       Shop: {src.get('shop_name')} | Price: {src.get('effective_price')} | Rating: {src.get('rating')} | Sold: {src.get('total_sold')}")

    except Exception as e:
        print(f"[ERROR] Verify failed: {e}")


def main():
    parser = argparse.ArgumentParser(description='Sync products to Elasticsearch (hybrid search)')
    parser.add_argument('--full', action='store_true', help='Full sync all products')
    parser.add_argument('--product-id', type=int, help='Sync single product by ID')
    parser.add_argument('--delete-product-id', type=int, help='Delete product from index')
    parser.add_argument('--update-sales', action='store_true', help='Update sales statistics')
    parser.add_argument('--recreate-index', action='store_true', help='Recreate index from scratch')
    parser.add_argument('--verify', action='store_true', help='Verify index and show stats')
    parser.add_argument('--embeddings', type=str, help='Path to embeddings.csv (from Colab)')
    parser.add_argument('--load-embeddings', type=str, help='Load embeddings only (no product resync)')

    args = parser.parse_args()

    es = get_es_client()

    if not es.ping():
        print("Cannot connect to Elasticsearch")
        sys.exit(1)

    print(f"Connected to Elasticsearch at {ES_HOST}")

    # Load embeddings if provided
    embeddings = None
    if args.embeddings:
        embeddings = load_embeddings_from_csv(args.embeddings)

    if args.recreate_index:
        recreate_index(es)
        sync_full(es, embeddings)
    elif args.full:
        create_index_if_not_exists(es)
        sync_full(es, embeddings)
    elif args.product_id:
        create_index_if_not_exists(es)
        sync_product(es, args.product_id)
    elif args.delete_product_id:
        delete_product(es, args.delete_product_id)
    elif args.update_sales:
        update_sales_statistics(es)
    elif args.load_embeddings:
        load_embeddings_to_index(es, args.load_embeddings)
    elif args.verify:
        verify_index(es)
    else:
        parser.print_help()
        sys.exit(1)

    if not args.verify:
        verify_index(es)

    print("\n[DONE] Done!")


if __name__ == "__main__":
    main()
