#!/usr/bin/env python3
"""
sync_es_suggestions.py - Sync trending/suggestion data to Elasticsearch

Index: foodtour_search_suggestions
Purpose: YouTube-like search suggestions with PhoBERT semantic matching

Data sources:
  1. suggestions.csv (exported from Colab) - trending queries + PhoBERT embeddings
  2. MySQL search_analytics table - fallback for trending computation

Usage:
    python sync_es_suggestions.py --from-csv suggestions.csv   # Load from Colab export
    python sync_es_suggestions.py --from-db                     # Compute from MySQL
    python sync_es_suggestions.py --recreate-index              # Recreate index + sync
    python sync_es_suggestions.py --verify                      # Check index

Author: FoodTourApp Team
"""

import argparse
import csv
import json
import re
import sys
import unicodedata
from collections import Counter, defaultdict
from datetime import datetime, timedelta
from typing import Optional, List, Dict, Any

import pymysql
from elasticsearch import Elasticsearch, helpers

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
ES_INDEX = 'foodtour_search_suggestions'
EMBEDDING_DIM = 768

# ================== INDEX MAPPING ==================
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
                    "min_gram": 1,
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
            # Query text (có dấu, đúng chính tả)
            "query_text": {
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

            # Query normalized (lowercase, bỏ dấu)
            "query_normalized": {
                "type": "text",
                "analyzer": "vietnamese_analyzer"
            },

            # No accent version (cho matching "pho" → "phở")
            "no_accent": {
                "type": "text",
                "analyzer": "vietnamese_analyzer",
                "fields": {
                    "autocomplete": {
                        "type": "text",
                        "analyzer": "autocomplete_analyzer",
                        "search_analyzer": "autocomplete_search"
                    }
                }
            },

            # PhoBERT embedding (768-dim, from Colab)
            "embedding": {
                "type": "dense_vector",
                "dims": EMBEDDING_DIM,
                "index": True,
                "similarity": "cosine"
            },

            # Trending metrics
            "total_searches": {"type": "long"},
            "searches_1d": {"type": "long"},
            "searches_7d": {"type": "long"},
            "searches_15d": {"type": "long"},
            "trending_multiplier": {"type": "float"},
            "is_trending": {"type": "boolean"},
            "es_weight": {"type": "float"},
            "last_searched_at": {"type": "date"},

            # Metadata
            "updated_at": {"type": "date"}
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


def sync_from_csv(es: Elasticsearch, csv_path: str):
    """
    Load suggestions from CSV exported by Colab.
    CSV columns: query_text, query_normalized, no_accent, embedding,
                 total_searches, searches_1d, searches_7d, searches_15d,
                 trending_multiplier, is_trending, es_weight, last_searched_at
    """
    print(f"[PROCESSING] Loading suggestions from {csv_path}...")

    actions = []
    with open(csv_path, 'r', encoding='utf-8-sig') as f:
        reader = csv.DictReader(f)
        for row in reader:
            doc = {
                'query_text': row['query_text'],
                'query_normalized': row.get('query_normalized', normalize_vietnamese(row['query_text'])),
                'no_accent': row.get('no_accent', normalize_vietnamese(row['query_text'])),
                'total_searches': int(row.get('total_searches', 0)),
                'searches_1d': int(row.get('searches_1d', 0)),
                'searches_7d': int(row.get('searches_7d', 0)),
                'searches_15d': int(row.get('searches_15d', 0)),
                'trending_multiplier': float(row.get('trending_multiplier', 1.0)),
                'is_trending': row.get('is_trending', 'False').lower() in ('true', '1', 'yes'),
                'es_weight': float(row.get('es_weight', 1)),
                'last_searched_at': row.get('last_searched_at', datetime.now().isoformat()),
                'updated_at': datetime.now().isoformat(),
            }

            # PhoBERT embedding
            if 'embedding' in row and row['embedding']:
                try:
                    emb = json.loads(row['embedding'])
                    if len(emb) == EMBEDDING_DIM:
                        doc['embedding'] = emb
                except (json.JSONDecodeError, TypeError):
                    pass

            doc_id = normalize_vietnamese(row['query_text']).replace(' ', '_')
            actions.append({
                "_index": ES_INDEX,
                "_id": doc_id,
                "_source": doc
            })

    if actions:
        success, failed = helpers.bulk(es, actions, raise_on_error=False)
        print(f"[OK] Indexed {success} suggestions")
        if failed:
            print(f"[WARN] Failed: {len(failed)}")
        es.indices.refresh(index=ES_INDEX)
    else:
        print("[WARN] No suggestions found in CSV")


def sync_from_db(es: Elasticsearch):
    """Compute trending from MySQL search_analytics table (fallback, no embeddings)"""
    print("[PROCESSING] Computing suggestions from search_analytics...")

    conn = get_mysql_connection()
    try:
        now = datetime.now()
        cut_1d = now - timedelta(days=1)
        cut_7d = now - timedelta(days=7)
        cut_15d = now - timedelta(days=15)

        with conn.cursor() as cursor:
            cursor.execute("""
                SELECT query_text, query_normalized, searched_at
                FROM search_analytics
                WHERE query_text IS NOT NULL AND query_text != ''
                ORDER BY searched_at DESC
            """)
            rows = cursor.fetchall()

        if not rows:
            print("[WARN] No search analytics data found")
            return

        # Group by normalized query
        stats = defaultdict(lambda: {
            'texts': [], 'total': 0,
            'c1d': 0, 'c7d': 0, 'c15d': 0, 'last': None
        })

        for row in rows:
            raw_q = row['query_text'].strip()
            norm = row['query_normalized'] or normalize_vietnamese(raw_q)
            ts = row['searched_at'] or now

            s = stats[norm]
            s['texts'].append(raw_q)
            s['total'] += 1
            if ts >= cut_1d:  s['c1d'] += 1
            if ts >= cut_7d:  s['c7d'] += 1
            if ts >= cut_15d: s['c15d'] += 1
            if s['last'] is None or ts > s['last']:
                s['last'] = ts

        # Build suggestion docs
        docs = []
        for norm, s in stats.items():
            recent = s['c15d']
            avg = recent / 15.0 if recent > 0 else 0
            mult = (s['c1d'] + s['c7d'] * 0.5) / (avg + 1e-6) if avg > 1e-6 else 1.0
            score = recent * (1 + 0.7 * (mult - 1))
            best_text = Counter(s['texts']).most_common(1)[0][0]

            docs.append({
                'query_text': best_text,
                'query_normalized': norm,
                'no_accent': normalize_vietnamese(best_text),
                'total_searches': s['total'],
                'searches_1d': s['c1d'],
                'searches_7d': s['c7d'],
                'searches_15d': s['c15d'],
                'trending_multiplier': round(mult, 4),
                'is_trending': mult >= 1.5,
                'es_weight': round(max(score, 0), 4),
                'last_searched_at': s['last'].isoformat() if s['last'] else None,
                'updated_at': now.isoformat(),
            })

        # Normalize es_weight to 1-10000 range
        if docs:
            max_w = max(d['es_weight'] for d in docs)
            for d in docs:
                nw = d['es_weight'] / max_w if max_w > 0 else 0
                d['es_weight'] = max(1, min(10000, int(1 + nw * 9999)))

        actions = []
        for d in docs:
            doc_id = d['query_normalized'].replace(' ', '_')
            actions.append({"_index": ES_INDEX, "_id": doc_id, "_source": d})

        if actions:
            success, failed = helpers.bulk(es, actions, raise_on_error=False)
            print(f"[OK] Indexed {success} suggestions from DB")
            es.indices.refresh(index=ES_INDEX)

    finally:
        conn.close()


def verify_index(es: Elasticsearch):
    try:
        count = es.count(index=ES_INDEX)['count']
        print(f"\n[STATS] Suggestion Index:")
        print(f"   Total: {count}")

        emb_count = es.count(index=ES_INDEX, body={
            "query": {"exists": {"field": "embedding"}}
        })['count']
        print(f"   With embeddings: {emb_count}")

        trending_count = es.count(index=ES_INDEX, body={
            "query": {"term": {"is_trending": True}}
        })['count']
        print(f"   Trending: {trending_count}")

        result = es.search(index=ES_INDEX, body={
            "size": 10,
            "sort": [{"es_weight": "desc"}],
            "_source": ["query_text", "total_searches", "is_trending", "es_weight"]
        })

        print(f"\n[INFO] Top suggestions:")
        for hit in result['hits']['hits']:
            src = hit['_source']
            badge = "TREND" if src.get('is_trending') else "     "
            print(f"   [{badge}] {src['query_text']:30} | searches={src.get('total_searches', 0):4d} | weight={src.get('es_weight', 0)}")

    except Exception as e:
        print(f"[ERROR] Verify failed: {e}")


def main():
    parser = argparse.ArgumentParser(description='Sync search suggestions to Elasticsearch')
    parser.add_argument('--from-csv', type=str, help='Load from Colab-exported CSV')
    parser.add_argument('--from-db', action='store_true', help='Compute from MySQL search_analytics')
    parser.add_argument('--recreate-index', action='store_true', help='Recreate index')
    parser.add_argument('--verify', action='store_true', help='Verify index')

    args = parser.parse_args()

    es = get_es_client()
    if not es.ping():
        print("Cannot connect to Elasticsearch")
        sys.exit(1)

    print(f"Connected to Elasticsearch at {ES_HOST}")

    if args.recreate_index:
        recreate_index(es)
        if args.from_csv:
            sync_from_csv(es, args.from_csv)
        elif args.from_db:
            sync_from_db(es)
    elif args.from_csv:
        create_index_if_not_exists(es)
        sync_from_csv(es, args.from_csv)
    elif args.from_db:
        create_index_if_not_exists(es)
        sync_from_db(es)
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
