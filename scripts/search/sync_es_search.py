#!/usr/bin/env python3
"""
sync_es_search.py - Sync products to Elasticsearch for search/filtering

Index: foodtour_products_search
Purpose: Full-text search, filtering, sorting for product listing API

Usage:
    python sync_es_search.py --full                    # Full sync all products
    python sync_es_search.py --product-id 123          # Sync single product
    python sync_es_search.py --delete-product-id 123   # Delete single product
    python sync_es_search.py --update-sales            # Update sales statistics
    python sync_es_search.py --recreate-index          # Recreate index from scratch

Author: FoodTourApp Team
"""

import argparse
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

# ================== ELASTICSEARCH MAPPING ==================
# Optimized for Vietnamese full-text search and filtering
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
            "shop_address": {"type": "text", "analyzer": "vietnamese_analyzer"},
            "shop_is_verified": {"type": "boolean"},
            "shop_is_active": {"type": "boolean"},
            
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
            
            # Rating & reviews
            "rating": {"type": "float"},
            "total_reviews": {"type": "long"},
            "rating_score": {"type": "float"},  # Weighted score for sorting
            
            # Sales statistics
            "total_sold": {"type": "long"},
            
            # Timestamps
            "created_at": {"type": "date"},
            "updated_at": {"type": "date"},
            
            # Combined search field
            "search_text": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            }
        }
    }
}


def get_mysql_connection():
    """Create MySQL connection"""
    return pymysql.connect(**MYSQL_CONFIG)


def get_es_client() -> Elasticsearch:
    """Create Elasticsearch client"""
    return Elasticsearch([ES_HOST])


def normalize_vietnamese(text: str) -> str:
    """
    Remove Vietnamese diacritics for fuzzy matching.
    'Phở bò' -> 'pho bo'
    """
    if not text:
        return ''
    
    # Handle special case for đ/Đ
    text = text.replace('Đ', 'D').replace('đ', 'd')
    
    # Normalize unicode and remove combining diacritical marks
    normalized = unicodedata.normalize('NFD', text)
    normalized = re.sub(r'[\u0300-\u036f]', '', normalized)
    
    return normalized.lower().strip()


def parse_json_array(json_str: str) -> List[str]:
    """Parse JSON array string, return empty list if invalid"""
    if not json_str:
        return []
    try:
        result = json.loads(json_str)
        return result if isinstance(result, list) else []
    except (json.JSONDecodeError, TypeError):
        return []


def calculate_effective_price(price: float, discount_price: Optional[float]) -> float:
    """Get the effective price (discount if available, else original)"""
    if discount_price and discount_price > 0 and discount_price < price:
        return discount_price
    return price


def calculate_discount_percentage(price: float, discount_price: Optional[float]) -> float:
    """Calculate discount percentage"""
    if not discount_price or discount_price <= 0 or discount_price >= price:
        return 0.0
    return round(((price - discount_price) / price) * 100, 2)


def calculate_rating_score(rating: float, total_reviews: int) -> float:
    """
    Calculate weighted rating score for sorting.
    Uses Wilson score interval for better ranking.
    Products with more reviews and higher ratings score better.
    """
    if not rating or not total_reviews:
        return 0.0
    
    # Simple weighted score: rating * log(reviews + 1)
    import math
    return round(rating * math.log(total_reviews + 1), 4)


def build_search_document(row: Dict[str, Any]) -> Dict[str, Any]:
    """Build Elasticsearch document from MySQL row"""
    
    # Parse JSON arrays
    image_urls = parse_json_array(row.get('image_urls'))
    ingredients = parse_json_array(row.get('ingredients'))
    nutrition_info = parse_json_array(row.get('nutrition_info'))
    tags = parse_json_array(row.get('tags'))
    
    # Calculate prices
    price = float(row.get('price') or 0)
    discount_price = float(row.get('discount_price') or 0) if row.get('discount_price') else None
    effective_price = calculate_effective_price(price, discount_price)
    has_discount = discount_price is not None and discount_price > 0 and discount_price < price
    discount_percentage = calculate_discount_percentage(price, discount_price)
    
    # Rating score for sorting
    rating = float(row.get('rating') or 0)
    total_reviews = int(row.get('total_reviews') or 0)
    rating_score = calculate_rating_score(rating, total_reviews)
    
    # Build search text (combined field for multi-match)
    search_parts = [
        row.get('name', ''),
        row.get('description', ''),
        row.get('shop_name', ''),
        row.get('category_name', ''),
        ' '.join(ingredients),
        ' '.join(tags)
    ]
    search_text = ' '.join(filter(None, search_parts))
    
    # Normalize Vietnamese text
    name_normalized = normalize_vietnamese(row.get('name', ''))
    description_normalized = normalize_vietnamese(row.get('description', ''))
    
    # Handle datetime fields
    created_at = row.get('created_at')
    updated_at = row.get('updated_at')
    if isinstance(created_at, datetime):
        created_at = created_at.isoformat()
    if isinstance(updated_at, datetime):
        updated_at = updated_at.isoformat()
    
    return {
        # IDs
        'id': row['id'],
        'shop_id': row.get('shop_id'),
        'category_id': row.get('category_id'),
        
        # Shop info
        'shop_name': row.get('shop_name'),
        'shop_city': row.get('shop_city', '').lower() if row.get('shop_city') else None,
        'shop_address': row.get('shop_address'),
        'shop_is_verified': bool(row.get('shop_is_verified')),
        'shop_is_active': bool(row.get('shop_is_active')),
        
        # Category
        'category_name': row.get('category_name'),
        
        # Product main fields
        'name': row.get('name'),
        'name_normalized': name_normalized,
        'description': row.get('description'),
        'description_normalized': description_normalized,
        
        # Price fields
        'price': price,
        'discount_price': discount_price,
        'effective_price': effective_price,
        'has_discount': has_discount,
        'discount_percentage': discount_percentage,
        
        # Arrays
        'image_urls': image_urls,
        'ingredients': ' '.join(ingredients),  # Text field for search
        'ingredients_list': ingredients,        # Keyword field
        'nutrition_info': nutrition_info,
        'tags': [t.lower() for t in tags],
        
        # Numeric fields
        'preparation_time': row.get('preparation_time'),
        'stock_quantity': row.get('stock_quantity', 0),
        'min_order_quantity': row.get('min_order_quantity', 1),
        'max_order_quantity': row.get('max_order_quantity', 999),
        
        # Status
        'is_available': bool(row.get('is_available')),
        
        # Rating & reviews
        'rating': rating,
        'total_reviews': total_reviews,
        'rating_score': rating_score,
        
        # Sales
        'total_sold': int(row.get('total_sold') or 0),
        
        # Timestamps
        'created_at': created_at,
        'updated_at': updated_at,
        
        # Search
        'search_text': search_text
    }


def create_index_if_not_exists(es: Elasticsearch):
    """Create the search index if it doesn't exist"""
    if not es.indices.exists(index=ES_INDEX):
        es.indices.create(index=ES_INDEX, body=INDEX_SETTINGS)
        print(f"✅ Created index: {ES_INDEX}")
    else:
        print(f"ℹ️  Index {ES_INDEX} already exists")


def recreate_index(es: Elasticsearch):
    """Delete and recreate the index"""
    if es.indices.exists(index=ES_INDEX):
        es.indices.delete(index=ES_INDEX)
        print(f"🗑️  Deleted index: {ES_INDEX}")
    
    es.indices.create(index=ES_INDEX, body=INDEX_SETTINGS)
    print(f"✅ Created index: {ES_INDEX}")


def get_products_query(product_id: Optional[int] = None) -> str:
    """Build SQL query to fetch products with shop and category info"""
    base_query = """
        SELECT 
            p.id,
            p.shop_id,
            p.category_id,
            p.name,
            p.description,
            p.price,
            p.discount_price,
            p.image_urls,
            p.ingredients,
            p.nutrition_info,
            p.tags,
            p.preparation_time,
            p.stock_quantity,
            p.min_order_quantity,
            p.max_order_quantity,
            p.is_available,
            p.rating,
            p.total_reviews,
            p.created_at,
            p.updated_at,
            s.shop_name,
            s.city as shop_city,
            s.address_line as shop_address,
            s.is_verified as shop_is_verified,
            s.is_active as shop_is_active,
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


def sync_full(es: Elasticsearch):
    """Full sync all products to Elasticsearch"""
    print("🔄 Starting full sync...")
    
    conn = get_mysql_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(get_products_query())
            rows = cursor.fetchall()
        
        if not rows:
            print("⚠️  No products found to sync")
            return
        
        # Build bulk actions
        actions = []
        for row in rows:
            doc = build_search_document(row)
            actions.append({
                "_index": ES_INDEX,
                "_id": str(row['id']),
                "_source": doc
            })
        
        # Bulk index
        success, failed = helpers.bulk(es, actions, raise_on_error=False)
        
        print(f"✅ Synced {success} products")
        if failed:
            print(f"⚠️  Failed: {len(failed)} products")
            for item in failed[:5]:  # Show first 5 errors
                print(f"   - {item}")
        
        # Refresh index
        es.indices.refresh(index=ES_INDEX)
        
    finally:
        conn.close()


def sync_product(es: Elasticsearch, product_id: int):
    """Sync a single product by ID"""
    print(f"🔄 Syncing product ID: {product_id}")
    
    conn = get_mysql_connection()
    try:
        with conn.cursor() as cursor:
            cursor.execute(get_products_query(product_id))
            row = cursor.fetchone()
        
        if not row:
            # Product not found or not available - delete from index
            print(f"⚠️  Product {product_id} not found or not available, removing from index")
            delete_product(es, product_id)
            return
        
        doc = build_search_document(row)
        
        es.index(
            index=ES_INDEX,
            id=str(product_id),
            document=doc
        )
        
        print(f"✅ Synced product: {row['name']} (ID: {product_id})")
        
    finally:
        conn.close()


def delete_product(es: Elasticsearch, product_id: int):
    """Delete a product from the index"""
    try:
        es.delete(index=ES_INDEX, id=str(product_id))
        print(f"✅ Deleted product ID: {product_id}")
    except NotFoundError:
        print(f"ℹ️  Product {product_id} not found in index (already deleted)")


def update_sales_statistics(es: Elasticsearch):
    """Update sales statistics for all products"""
    print("🔄 Updating sales statistics...")
    
    conn = get_mysql_connection()
    try:
        query = """
            SELECT 
                p.id,
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
        
        # Bulk update
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
            print(f"✅ Updated sales for {success} products")
            if failed:
                print(f"⚠️  Failed: {len(failed)}")
        
    finally:
        conn.close()


def verify_index(es: Elasticsearch):
    """Verify index and show sample documents"""
    try:
        # Get count
        count = es.count(index=ES_INDEX)['count']
        print(f"\n📊 Index Statistics:")
        print(f"   Total documents: {count}")
        
        # Sample documents
        result = es.search(
            index=ES_INDEX,
            body={
                "size": 3,
                "query": {"match_all": {}},
                "_source": ["id", "name", "shop_name", "effective_price", "rating", "total_sold"]
            }
        )
        
        print(f"\n📋 Sample documents:")
        for hit in result['hits']['hits']:
            src = hit['_source']
            print(f"   [{src['id']}] {src['name']}")
            print(f"       Shop: {src.get('shop_name')} | Price: {src.get('effective_price')} | Rating: {src.get('rating')} | Sold: {src.get('total_sold')}")
        
    except Exception as e:
        print(f"❌ Verify failed: {e}")


def main():
    parser = argparse.ArgumentParser(description='Sync products to Elasticsearch for search')
    parser.add_argument('--full', action='store_true', help='Full sync all products')
    parser.add_argument('--product-id', type=int, help='Sync single product by ID')
    parser.add_argument('--delete-product-id', type=int, help='Delete product from index')
    parser.add_argument('--update-sales', action='store_true', help='Update sales statistics')
    parser.add_argument('--recreate-index', action='store_true', help='Recreate index from scratch')
    parser.add_argument('--verify', action='store_true', help='Verify index and show stats')
    
    args = parser.parse_args()
    
    # Connect to Elasticsearch
    es = get_es_client()
    
    if not es.ping():
        print("❌ Cannot connect to Elasticsearch")
        sys.exit(1)
    
    print(f"✅ Connected to Elasticsearch at {ES_HOST}")
    
    # Handle commands
    if args.recreate_index:
        recreate_index(es)
        sync_full(es)
    elif args.full:
        create_index_if_not_exists(es)
        sync_full(es)
    elif args.product_id:
        create_index_if_not_exists(es)
        sync_product(es, args.product_id)
    elif args.delete_product_id:
        delete_product(es, args.delete_product_id)
    elif args.update_sales:
        update_sales_statistics(es)
    elif args.verify:
        verify_index(es)
    else:
        parser.print_help()
        sys.exit(1)
    
    # Always verify at the end
    if not args.verify:
        verify_index(es)
    
    print("\n🎉 Done!")


if __name__ == "__main__":
    main()
