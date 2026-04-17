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