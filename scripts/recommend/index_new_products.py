"""
index_new_products.py — Sinh embedding cho sản phẩm mới chưa có trong ES products_recommend.

Tại sao cần script này?
  - sync_es_rcm_behavior.py chỉ chạy sau khi retrain (Colab) → products thêm sau retrain không có embedding
  - Script này dùng phần TEXT BRANCH của Product Tower (đã train) để sinh embedding cho sản phẩm mới
  - Cụ thể: text_projection(SentenceTransformer(name + desc + tags + ingredients)) → 64-dim
  - Không phải full Product Tower output (thiếu id_emb chưa được train), nhưng là proxy tốt nhất
    có thể làm mà không cần retrain. Khi retrain xong, sync_es_rcm_behavior.py sẽ ghi đè bằng
    embedding thật.

Usage:
  python index_new_products.py [--api-url http://localhost:8080] [--api-token <JWT>]

  Nếu không có API token, truyền products qua JSON file:
  python index_new_products.py --products-file products.json

  products.json format:
  [{"product_id": 1, "name": "...", "description": "...", "ingredients": "...", "tags": "..."}]
"""

import argparse
import json
import os
import sys
import pickle
import requests
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F

# ══════════════════════════════════════════════════════════════════════════════
# CONFIGURATION
# ══════════════════════════════════════════════════════════════════════════════

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_DIR = os.path.join(
    SCRIPT_DIR, "..", "..", "src", "main", "resources",
    "models", "FoodRecommendSearchByBehavior", "model_output"
)
ES_URL = "http://localhost:9200"
INDEX_NAME = "products_recommend"

# ══════════════════════════════════════════════════════════════════════════════
# PRODUCT TOWER (text branch chỉ) — phải khớp với kiến trúc trong Colab
# ══════════════════════════════════════════════════════════════════════════════

class ProductTowerTextOnly(nn.Module):
    """
    Chỉ dùng phần TEXT của Product Tower để sinh embedding cho sản phẩm mới.

    Full Product Tower:
        id_emb  = product_id_embedding(product_idx)     # 64-dim — học từ interaction
        txt_emb = text_projection(sentence_emb)          # 384 → 64-dim
        output  = L2_normalize(fusion(concat(id_emb, txt_emb)))

    Text-only (cho sản phẩm mới chưa có trong training set):
        txt_emb = text_projection(sentence_emb)          # 384 → 64-dim
        output  = L2_normalize(txt_emb)                 # không có id_emb, không có fusion

    Trade-off: thiếu collaborative filtering signal (id_emb), nhưng đây là tốt nhất
    có thể làm không retrain. Khi retrain xong, sync_es_rcm_behavior.py ghi đè bằng
    embedding thật (cả id_emb + fusion).
    """
    def __init__(self, text_embedding_dim=384, hidden_dim=128, embedding_dim=64):
        super().__init__()
        self.text_projection = nn.Sequential(
            nn.Linear(text_embedding_dim, hidden_dim),
            nn.LayerNorm(hidden_dim),
            nn.ReLU(),
            nn.Dropout(0.2),
            nn.Linear(hidden_dim, embedding_dim)
        )

    def forward(self, text_embedding):
        proj = self.text_projection(text_embedding)
        return F.normalize(proj, p=2, dim=-1)


def load_text_projection_weights(model_path: str, config: dict) -> ProductTowerTextOnly:
    """
    Load phần text_projection weights từ two_tower_model.pt.
    Keys trong state_dict có dạng: product_tower.text_projection.*
    """
    text_dim = config.get("text_embedding_dim", 384)
    hidden_dim = config.get("hidden_dim", 128)
    emb_dim = config.get("embedding_dim", 64)

    model = ProductTowerTextOnly(text_dim, hidden_dim, emb_dim)

    full_state = torch.load(model_path, map_location="cpu", weights_only=True)
    text_proj_state = {}
    prefix = "product_tower.text_projection."
    for k, v in full_state.items():
        if k.startswith(prefix):
            text_proj_state[k[len(prefix):]] = v

    if not text_proj_state:
        raise ValueError(
            f"Không tìm thấy 'product_tower.text_projection.*' trong {model_path}. "
            "Kiểm tra lại file model hoặc tên keys."
        )

    model.text_projection.load_state_dict(text_proj_state)
    model.eval()
    print(f"✅ Loaded text_projection weights từ {model_path}")
    return model


# ══════════════════════════════════════════════════════════════════════════════
# HELPERS
# ══════════════════════════════════════════════════════════════════════════════

def get_existing_product_ids_in_es() -> set:
    """Lấy tất cả product_id đã có trong ES index."""
    try:
        resp = requests.post(
            f"{ES_URL}/{INDEX_NAME}/_search",
            json={"query": {"match_all": {}}, "size": 10000, "_source": ["product_id"]},
            timeout=10
        )
        if resp.status_code != 200:
            return set()
        hits = resp.json().get("hits", {}).get("hits", [])
        return {h["_source"]["product_id"] for h in hits if "product_id" in h.get("_source", {})}
    except Exception as e:
        print(f"⚠️ Không lấy được danh sách products từ ES: {e}")
        return set()


def build_product_text(product: dict) -> str:
    """Ghép text features cho SentenceTransformer — khớp với cách làm trong Colab."""
    def parse_json_arr(val):
        if not val:
            return []
        if isinstance(val, list):
            return val
        try:
            return json.loads(val) if isinstance(val, str) else []
        except Exception:
            return []

    name = product.get("name", "")
    desc = product.get("description", "")
    ingredients = " ".join(parse_json_arr(product.get("ingredients", "")))
    tags = " ".join(parse_json_arr(product.get("tags", "")))
    return f"{name} {desc} {ingredients} {tags}".strip()


def fetch_products_from_api(api_url: str, api_token: str) -> list:
    """Fetch tất cả products từ Spring Boot API."""
    headers = {"Authorization": f"Bearer {api_token}"}
    try:
        resp = requests.get(f"{api_url}/api/admin/products/all", headers=headers, timeout=30)
        if resp.status_code == 200:
            data = resp.json()
            return data.get("data", data) if isinstance(data, dict) else data
        print(f"⚠️ API trả về {resp.status_code}: {resp.text[:200]}")
        return []
    except Exception as e:
        print(f"⚠️ Không gọi được API: {e}")
        return []


def index_to_es(documents: list) -> tuple:
    """Bulk index documents vào ES (không xóa index cũ — chỉ upsert)."""
    if not documents:
        return 0, 0

    bulk_body = ""
    for doc in documents:
        action = json.dumps({"index": {"_index": INDEX_NAME, "_id": str(doc["product_id"])}})
        bulk_body += f"{action}\n{json.dumps(doc)}\n"

    resp = requests.post(
        f"{ES_URL}/_bulk",
        data=bulk_body,
        headers={"Content-Type": "application/x-ndjson"},
        timeout=30
    )
    if resp.status_code != 200:
        print(f"❌ Bulk index thất bại: {resp.status_code}")
        return 0, len(documents)

    result = resp.json()
    success = sum(1 for item in result.get("items", []) if "error" not in item.get("index", {}))
    failed = len(result.get("items", [])) - success
    return success, failed


# ══════════════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(description="Sinh embedding cho sản phẩm mới chưa có trong ES")
    parser.add_argument("--api-url", default="http://localhost:8080")
    parser.add_argument("--api-token", default="")
    parser.add_argument("--products-file", default="", help="JSON file chứa products (thay thế cho API)")
    parser.add_argument("--model-dir", default=MODEL_DIR)
    parser.add_argument("--es-url", default=ES_URL)
    args = parser.parse_args()

    es_base = args.es_url
    model_dir = args.model_dir

    # ── 1. Load config ─────────────────────────────────────────────────────────
    config_path = os.path.join(model_dir, "config.json")
    if not os.path.exists(config_path):
        print(f"❌ Không tìm thấy config.json tại {config_path}")
        sys.exit(1)
    with open(config_path, "r") as f:
        config = json.load(f)
    print(f"📋 Config: embedding_dim={config.get('embedding_dim')}, trained_at={config.get('trained_at')}")

    # ── 2. Kiểm tra products đã có trong ES ───────────────────────────────────
    print("\n🔍 Lấy danh sách products đã có trong ES...")
    existing_ids = get_existing_product_ids_in_es()
    print(f"   ES hiện có {len(existing_ids)} products")

    # ── 3. Lấy tất cả products cần embed ──────────────────────────────────────
    if args.products_file:
        with open(args.products_file, "r", encoding="utf-8") as f:
            all_products = json.load(f)
        if isinstance(all_products, dict) and "data" in all_products:
            all_products = all_products["data"]
        print(f"   Đọc {len(all_products)} products từ {args.products_file}")
    elif args.api_token:
        print(f"\n📡 Fetch products từ API {args.api_url}...")
        all_products = fetch_products_from_api(args.api_url, args.api_token)
        print(f"   Fetch được {len(all_products)} products")
    else:
        print("❌ Cần --api-token hoặc --products-file")
        sys.exit(1)

    # Lọc ra sản phẩm chưa có embedding
    new_products = [
        p for p in all_products
        if p.get("product_id") not in existing_ids and p.get("product_id") is not None
    ]

    if not new_products:
        print("\n✅ Tất cả products đã có embedding trong ES. Không cần làm gì thêm.")
        return

    print(f"\n🆕 Tìm thấy {len(new_products)} sản phẩm mới chưa có embedding")

    # ── 4. Load model ──────────────────────────────────────────────────────────
    model_pt_path = os.path.join(model_dir, "two_tower_model.pt")
    if not os.path.exists(model_pt_path):
        print(f"❌ Không tìm thấy two_tower_model.pt tại {model_pt_path}")
        sys.exit(1)

    print("\n🤖 Load text_projection weights từ Product Tower...")
    text_model = load_text_projection_weights(model_pt_path, config)

    print("📥 Load SentenceTransformer (paraphrase-multilingual-MiniLM-L12-v2)...")
    try:
        from sentence_transformers import SentenceTransformer
    except ImportError:
        print("❌ Cần cài: pip install sentence-transformers")
        sys.exit(1)
    st_model = SentenceTransformer("paraphrase-multilingual-MiniLM-L12-v2")

    # ── 5. Generate embeddings ─────────────────────────────────────────────────
    print(f"\n⚙️  Sinh embeddings cho {len(new_products)} sản phẩm...")
    documents = []

    texts = [build_product_text(p) for p in new_products]
    print("   Encoding với SentenceTransformer...")
    # batch encode tất cả cùng lúc — nhanh hơn nhiều so với từng cái một
    st_embeddings = st_model.encode(texts, batch_size=64, show_progress_bar=True, convert_to_numpy=True)

    print("   Chạy qua text_projection của Product Tower...")
    with torch.no_grad():
        st_tensor = torch.tensor(st_embeddings, dtype=torch.float32)
        # Batch inference
        batch_size = 256
        all_embs = []
        for i in range(0, len(st_tensor), batch_size):
            batch = st_tensor[i:i + batch_size]
            emb = text_model(batch)  # (batch, 64)
            all_embs.append(emb.cpu().numpy())
        final_embeddings = np.concatenate(all_embs, axis=0)  # (N, 64)

    for idx, product in enumerate(new_products):
        pid = product["product_id"]
        emb = final_embeddings[idx].tolist()

        doc = {
            "product_id": pid,
            "name": product.get("name", f"Product {pid}"),
            "embedding": emb,
            "embedding_source": "text_only"  # đánh dấu để biết đây chưa phải full Two-Tower
        }
        if product.get("category_id"):
            doc["category_id"] = int(product["category_id"])
        if product.get("shop_id"):
            doc["shop_id"] = int(product["shop_id"])
        if product.get("rating"):
            doc["rating"] = float(product["rating"])
        documents.append(doc)

    # ── 6. Index vào ES (upsert, không xóa cái cũ) ────────────────────────────
    print(f"\n📤 Upsert {len(documents)} documents vào ES index '{INDEX_NAME}'...")
    success, failed = index_to_es(documents)

    requests.post(f"{es_base}/{INDEX_NAME}/_refresh")
    print(f"✅ Done! Success: {success}, Failed: {failed}")
    print(f"\n💡 Sản phẩm mới đã có embedding text-only trong ES.")
    print(f"   Sau lần retrain tiếp theo, chạy sync_es_rcm_behavior.py để ghi đè bằng embedding thật.")


if __name__ == "__main__":
    main()
