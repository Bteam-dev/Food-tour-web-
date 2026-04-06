# Hybrid Search Implementation - Shopee-Style
## Phase 1: Architecture Design & Dataset Preparation

---

## 🎯 Goal: Search như Shopee

**User searches:** "phở bò ngon rẻ quận 1"

**Expected results:**
1. **Semantic matching**: "phở bò Hà Nội giá rẻ" (khác từ nhưng cùng ý nghĩa)
2. **Keyword matching**: "phở bò", "quận 1" (exact match)
3. **Personalized**: Boost products user đã xem/thích
4. **Location-aware**: Ưu tiên shops ở quận 1
5. **Business rules**: Promoted products lên top

---

## 📐 Complete Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     USER QUERY                                  │
│              "phở bò ngon rẻ quận 1"                            │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                  QUERY PROCESSING                               │
│  • Tokenization: ["phở", "bò", "ngon", "rẻ", "quận 1"]       │
│  • Intent detection: food_search, location=quận1              │
│  • Generate embedding: [0.23, -0.45, 0.67, ...] (768 dims)   │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│              ELASTICSEARCH HYBRID QUERY                         │
│                                                                 │
│  ┌──────────────────┐  ┌──────────────────┐                   │
│  │  Vector Search   │  │  Keyword Search  │                   │
│  │  (kNN semantic)  │  │  (BM25 full-text)│                   │
│  │                  │  │                  │                   │
│  │  Match: "bún bò" │  │  Match: "phở bò" │                   │
│  │  Score: 0.85     │  │  Score: 0.92     │                   │
│  └──────────────────┘  └──────────────────┘                   │
│            ↓                    ↓                               │
│  ┌──────────────────────────────────────┐                     │
│  │   RRF (Reciprocal Rank Fusion)      │                     │
│  │   Combine scores without tuning     │                     │
│  │   Final Score = f(rank1, rank2)     │                     │
│  └──────────────────────────────────────┘                     │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                 PERSONALIZATION LAYER                           │
│                                                                 │
│  User Profile:                                                 │
│  • View history: [phở, bún, bánh mì]                          │
│  • Favorite shops: [Shop A, Shop C]                           │
│  • Price preference: < 50k                                     │
│  • Location: Quận 1                                            │
│                                                                 │
│  Re-ranking Rules:                                             │
│  • +20% if user viewed this product before                     │
│  • +15% if shop in user's favorites                           │
│  • +10% if price matches user's range                         │
│  • +10% if shop in same district                              │
│  • +5% if category matches user preference                    │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                  BUSINESS RULES LAYER                           │
│                                                                 │
│  • Promoted products: Move to top 3                            │
│  • Out of stock: Penalty -50%                                  │
│  • New products (< 7 days): Boost +10%                         │
│  • High rating (> 4.5): Boost +5%                              │
│  • Diversity: Max 2 products per shop in top 10                │
└─────────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────────┐
│                      FINAL RESULTS                              │
│  1. Phở bò Hà Nội - Shop A (Quận 1) - 45k ⭐4.8               │
│  2. Phở bò Nam Định - Shop C (Quận 1) - 40k ⭐4.5             │
│  3. Bún bò Huế - Shop B (Quận 3) - 50k ⭐4.9                  │
│  ...                                                            │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🗂️ Phase Breakdown

### **Phase 1: Dataset Preparation & Model Training** (1-2 weeks)
- Collect training data from your DB
- Label search query → product pairs
- Fine-tune Vietnamese embedding model
- Evaluate model performance

### **Phase 2: Elasticsearch Setup** (3-5 days)
- Update ES mapping for hybrid search
- Create new index with kNN support
- Migrate data with new embeddings

### **Phase 3: Backend Implementation** (1 week)
- HybridSearchService (vector + keyword)
- RRF score fusion
- User tracking service (view history, clicks)

### **Phase 4: Personalization** (1 week)
- User profile builder
- Re-ranking engine
- A/B testing framework

### **Phase 5: Monitoring & Optimization** (ongoing)
- Search analytics dashboard
- Click-through rate tracking
- Continuous model improvement

---

## 📊 Part 1: Training Dataset Creation

### 1.1 Data Collection Strategy

**Bạn cần 3 loại data:**

#### A. **Product Corpus** (đã có trong DB)
```sql
SELECT 
    p.id,
    p.name,
    p.description,
    c.name as category_name,
    s.shop_name,
    s.city,
    p.price,
    p.rating,
    GROUP_CONCAT(DISTINCT tag) as tags
FROM product p
JOIN category c ON p.category_id = c.id
JOIN shop s ON p.shop_id = s.id
WHERE p.is_available = true
  AND s.is_verified = true;
```

**Export to CSV:**
```csv
product_id,name,description,category,shop,city,price,rating,tags
1,"Phở bò Hà Nội","Phở bò truyền thống...","Món phở","Phở 24","Hồ Chí Minh",45000,4.8,"phở,bò,truyền thống"
2,"Bún bò Huế","Bún bò cay nồng...","Món bún","Bún Huế Mẹ Già","Hà Nội",50000,4.9,"bún,bò,huế,cay"
```

#### B. **Search Logs** (cần bắt đầu collect)

**Tạo bảng search_logs:**
```sql
CREATE TABLE search_logs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    query TEXT,
    clicked_product_ids JSON,  -- [12, 45, 67]
    viewed_product_ids JSON,   -- [12, 23, 45, 67, 89]
    filters JSON,              -- {"city":"HCM", "priceMax":100000}
    result_count INT,
    search_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_time (user_id, search_time),
    INDEX idx_query (query(255))
);
```

**Log mỗi search request:**
```java
public void logSearch(Integer userId, String query, 
                     List<Integer> resultIds, Map<String, Object> filters) {
    SearchLog log = new SearchLog();
    log.setUserId(userId);
    log.setQuery(query);
    log.setFilters(objectMapper.writeValueAsString(filters));
    log.setResultCount(resultIds.size());
    searchLogRepository.save(log);
}
```

#### C. **Labeled Query-Product Pairs** (tạo bằng search logs + manual)

**Format:**
```csv
query,product_id,relevance_score,reason
"phở bò ngon",1,5,clicked_and_purchased
"phở bò ngon",12,4,clicked_only
"phở bò ngon",23,3,viewed_only
"phở bò ngon",45,1,shown_but_not_clicked
"bún bò huế",2,5,clicked_and_purchased
"món ăn sáng rẻ",34,4,clicked_only
```

**Relevance scores:**
- 5 = Perfect match (clicked → purchased)
- 4 = Good match (clicked, viewed > 30s)
- 3 = Relevant (viewed)
- 2 = Somewhat relevant (shown, scrolled past)
- 1 = Not relevant (shown but ignored)

---

### 1.2 Generate Training Data from Existing DB

**Script to create initial training pairs:**

```python
# scripts/ml/generate_training_data.py
import pymysql
import pandas as pd
from typing import List, Tuple

class TrainingDataGenerator:
    def __init__(self, db_config):
        self.conn = pymysql.connect(**db_config)
    
    def get_all_products(self) -> pd.DataFrame:
        """Fetch all active products"""
        query = """
        SELECT 
            p.id,
            p.name,
            p.description,
            c.name as category_name,
            s.shop_name,
            s.city,
            p.price,
            p.rating,
            p.tags
        FROM product p
        JOIN category c ON p.category_id = c.id
        JOIN shop s ON p.shop_id = s.id
        WHERE p.is_available = 1
          AND s.is_verified = 1
        """
        return pd.read_sql(query, self.conn)
    
    def generate_synthetic_queries(self, product: dict) -> List[str]:
        """
        Generate synthetic search queries for a product
        
        Examples:
        Product: "Phở bò Hà Nội"
        Queries: 
          - "phở bò" (exact)
          - "phở" (partial)
          - "món phở ngon" (with adjective)
          - "ăn phở ở {city}" (with location)
        """
        queries = []
        name = product['name'].lower()
        category = product['category_name'].lower()
        city = product['city'].lower()
        
        # Exact name
        queries.append(name)
        
        # Partial name (first 2-3 words)
        words = name.split()
        if len(words) >= 2:
            queries.append(' '.join(words[:2]))
        
        # Category + main keyword
        if 'phở' in name:
            queries.append('phở')
            queries.append('món phở')
            queries.append(f'phở {city}')
        elif 'bún' in name:
            queries.append('bún')
            queries.append('món bún')
        elif 'cơm' in name:
            queries.append('cơm')
            queries.append('cơm văn phòng')
        
        # With location
        queries.append(f'{category} {city}')
        
        # With price range
        price = product['price']
        if price < 30000:
            queries.append(f'{name} giá rẻ')
        elif price < 50000:
            queries.append(f'{name} bình dân')
        
        return queries
    
    def create_training_pairs(self) -> pd.DataFrame:
        """Create query → product pairs with relevance scores"""
        products = self.get_all_products()
        
        training_data = []
        
        for _, product in products.iterrows():
            queries = self.generate_synthetic_queries(product.to_dict())
            
            for query in queries:
                # Exact match = high relevance
                if query.lower() == product['name'].lower():
                    score = 5
                # Partial match = medium relevance
                elif query in product['name'].lower():
                    score = 4
                # Category match = lower relevance
                else:
                    score = 3
                
                training_data.append({
                    'query': query,
                    'product_id': product['id'],
                    'product_name': product['name'],
                    'relevance_score': score
                })
        
        return pd.DataFrame(training_data)

# Usage:
if __name__ == '__main__':
    generator = TrainingDataGenerator({
        'host': 'localhost',
        'port': 3307,
        'user': 'root',
        'password': 'your_password',
        'database': 'foodtour'
    })
    
    training_df = generator.create_training_pairs()
    training_df.to_csv('training_data_initial.csv', index=False)
    print(f"Generated {len(training_df)} training pairs")
```

**Run:**
```bash
cd scripts/ml
python generate_training_data.py
# Output: training_data_initial.csv (5000-10000 pairs)
```

---

### 1.3 Manual Labeling (High Quality Data)

**Option 1: Label Studio (Open Source)**
```bash
# Install Label Studio
pip install label-studio

# Start server
label-studio start

# Import your CSV
# UI: http://localhost:8080
```

**Labeling interface config:**
```xml
<View>
  <Header value="Search Relevance Labeling"/>
  
  <Text name="query" value="$query"/>
  <Text name="product" value="$product_name"/>
  <Text name="description" value="$description"/>
  
  <Rating name="relevance" toName="query" maxRating="5" icon="star"/>
  
  <Choices name="reason" toName="query" choice="single">
    <Choice value="exact_match"/>
    <Choice value="semantic_match"/>
    <Choice value="category_match"/>
    <Choice value="not_relevant"/>
  </Choices>
</View>
```

**Option 2: Simple Google Sheets**
```
Query | Product Name | Description | Your Rating (1-5) | Notes
phở bò | Phở bò Hà Nội | ... | 5 | Perfect match
phở bò | Bún bò Huế | ... | 2 | Wrong dish type
```

---

## 📝 Next Steps (Will create in separate documents)

**I will create these detailed guides:**

1. ✅ **`HYBRID_SEARCH_PHASE1_DATASET.md`** (this file)
   - Data collection strategy
   - Training data generation script
   - Manual labeling guide

2. 📄 **`HYBRID_SEARCH_PHASE2_MODEL_TRAINING.md`** (next)
   - Google Colab notebook setup
   - Fine-tune Sentence-BERT for Vietnamese
   - Model evaluation & export
   - Deploy to Ollama

3. 📄 **`HYBRID_SEARCH_PHASE3_ELASTICSEARCH.md`**
   - ES mapping for hybrid search
   - kNN configuration
   - RRF query syntax
   - Data migration script

4. 📄 **`HYBRID_SEARCH_PHASE4_BACKEND.md`**
   - HybridSearchService implementation
   - User tracking service
   - Personalization engine
   - A/B testing framework

5. 📄 **`HYBRID_SEARCH_PHASE5_EVALUATION.md`**
   - Metrics: MRR, NDCG, Precision@K
   - Click-through rate tracking
   - A/B test analysis

---

## 🚀 Quick Start (What to do NOW)

### Step 1: Run training data generation script
```bash
cd scripts/ml
mkdir -p scripts/ml
# I'll create the Python script next
python generate_training_data.py
```

### Step 2: Start collecting search logs
```java
// Add to ProductController search endpoint
@Autowired
private SearchLogService searchLogService;

@GetMapping("/search")
public Page<ProductResponseDTO> search(@RequestParam String keyword, ...) {
    Page<ProductResponseDTO> results = productService.search(keyword, ...);
    
    // Log search
    searchLogService.logSearch(
        getCurrentUserId(),
        keyword,
        results.getContent().stream().map(p -> p.getId()).toList(),
        Map.of("keyword", keyword, "city", city, ...)
    );
    
    return results;
}
```

### Step 3: Manual labeling (100-200 pairs for validation)
- Pick 50 common queries
- Label top 10 results for each
- Use Google Sheets or Label Studio

---

## ⏱️ Timeline Estimate

| Phase | Task | Time | Deliverable |
|-------|------|------|-------------|
| **Phase 1** | Data collection | 3 days | training_data.csv (5k pairs) |
| | Manual labeling | 2 days | validation_data.csv (500 pairs) |
| **Phase 2** | Model training (Colab) | 2-4 hours | fine_tuned_model.bin |
| | Deploy to Ollama | 1 day | ollama pull foodtour-embed |
| **Phase 3** | ES mapping update | 1 day | New index with kNN |
| | Data migration | 1 day | All products with new embeddings |
| **Phase 4** | HybridSearchService | 2 days | Java implementation |
| | User tracking | 2 days | Search logs + user profile |
| | Personalization | 3 days | Re-ranking engine |
| **Phase 5** | Testing & tuning | 1 week | Production-ready |

**Total: 3-4 weeks end-to-end**

---

## ❓ FAQ

**Q: Cần bao nhiêu data để train?**  
A: Tối thiểu 1000 pairs, tốt nhất 5000-10000 pairs. Chất lượng > Số lượng.

**Q: Có thể dùng pre-trained model không?**  
A: Có (PhoBERT, viBERT), nhưng fine-tune sẽ tốt hơn 20-30% cho domain-specific (food search).

**Q: Training mất bao lâu?**  
A: 2-4 hours trên Google Colab (free T4 GPU). Không cần GPU mạnh.

**Q: Deploy model như thế nào?**  
A: Convert sang ONNX → Load vào Ollama → Gọi từ Java như hiện tại.

---

Tôi đã tạo xong **Phase 1 document**. Bạn muốn tôi:
1. **Tạo Python script** để generate training data từ DB của bạn?
2. **Tạo Google Colab notebook** để train model?
3. **Tiếp tục viết các Phase documents** còn lại?

Hoặc bạn muốn tôi implement luôn phần nào? (VD: SearchLogService, training script, etc.)
