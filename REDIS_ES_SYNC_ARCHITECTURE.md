# Redis Architecture - Elasticsearch Sync & Rate Limiting

## Overview

Redis được sử dụng cho 3 tính năng chính:
1. **Token Blacklist** - Auth token invalidation với auto-expiry
2. **Elasticsearch Event Queue** - Async batch sync cho 2 ES indexes
3. **Rate Limiting** - Bảo vệ auth endpoints (login, register, OTP)

---

# Part 1: Elasticsearch Event Queue

Thay vì gọi Python script mỗi khi Product thay đổi, hệ thống giờ sử dụng **Redis Event Queue** với **Background Consumers** để batch sync lên Elasticsearch.

## Architecture Flow

```
Product Change (JPA Entity)
         ↓
   JPA Listener
         ↓
    ┌────────────────────┐
    │  Push to 2 Queues  │
    └────────────────────┘
         ↓         ↓
   ┌─────────┐  ┌──────────┐
   │ Chatbot │  │  Search  │
   │  Queue  │  │  Queue   │
   └─────────┘  └──────────┘
         ↓         ↓
   Background    Background
   Consumer      Consumer
         ↓         ↓
     Ollama      Direct
   Embedding     Indexing
         ↓         ↓
   ┌─────────────────────────┐
   │ Elasticsearch (2 indexes)│
   │ - foodtour_products_chatbot │
   │ - foodtour_products_search  │
   └─────────────────────────┘
```

## Components

### 1. **Event Model**
- **File**: `com.example.FoodTourApp.event.EsProductSyncEvent`
- **Fields**: productId, action (INDEX/DELETE), timestamp
- **Redis Serialization**: Jackson JSON

### 2. **Producers** (Push events to queues)

#### Chatbot Producer
- **Interface**: `EsChatbotSyncProducer`
- **Implementation**: `EsChatbotSyncProducerImpl`
- **Queue**: `es:chatbot:sync:queue`
- **Purpose**: Sync products with **embedding vectors** for RAG

#### Search Producer
- **Interface**: `EsProductSyncProducer`
- **Implementation**: `EsProductSyncProducerImpl`
- **Queue**: `es:search:sync:queue`
- **Purpose**: Sync products for **search/filter** API

### 3. **Consumers** (Process events in batches)

#### Chatbot Consumer
- **Class**: `EsChatbotSyncConsumer`
- **Index**: `foodtour_products_chatbot`
- **Batch Size**: 50 events
- **Poll Interval**: 5 seconds
- **Special Features**:
  - Generates **768-dim embedding vectors** via Ollama
  - Creates structured `context_text` for RAG
  - Dense vector field with cosine similarity

**Document Structure**:
```json
{
  "id": 123,
  "name": "Phở bò",
  "shop_name": "Phở Hà Nội",
  "category_name": "Món phở",
  "context_text": "===PRODUCT===\nMón: Phở bò\n...",
  "embedding": [0.123, -0.456, ...],  // 768 dimensions
  "price": 50000,
  "rating": 4.5,
  ...
}
```

#### Search Consumer
- **Class**: `EsProductSyncConsumer`
- **Index**: `foodtour_products_search`
- **Batch Size**: 50 events
- **Poll Interval**: 5 seconds
- **Features**: Fast indexing without embeddings

### 4. **JPA Listener**
- **File**: `ProductEsSyncListener`
- **Triggers**: `@PostPersist`, `@PostUpdate`, `@PostRemove`
- **Behavior**: Pushes events to BOTH queues (chatbot + search)

## Benefits vs Python Script

| Aspect | Python Script (Old) | Redis Queue (New) |
|--------|---------------------|-------------------|
| **Performance** | Blocking, slow | Non-blocking, async |
| **Scalability** | 1 product at a time | Batch processing (50x) |
| **Reliability** | Fails silently | Persistent queue, retry |
| **Monitoring** | Hard to track | Redis queue metrics |
| **Embedding** | Python + Ollama | Java + Ollama (same!) |
| **Dependencies** | Python + pymysql + elasticsearch | Pure Java |

## Key Improvements

### 1. **Deduplication**
If multiple events for same product arrive, only latest is processed:
```java
// Keep latest event per productId
Map<Integer, EsProductSyncEvent> deduped = new LinkedHashMap<>();
for (EsProductSyncEvent event : events) {
    deduped.put(event.getProductId(), event);  // Latest wins
}
```

### 2. **Batch Processing**
Fetches all products in 1 DB query instead of N queries:
```java
List<Product> products = productRepository.findAllById(toIndex);
```

### 3. **Bulk Elasticsearch Operations**
Uses ES bulk API for better performance:
```java
BulkRequest bulkRequest = BulkRequest.of(br -> br
    .operations(operations)  // 50 operations in 1 request
    .refresh(Refresh.False)
);
```

## Configuration

**application.properties**:
```properties
# Elasticsearch
elasticsearch.host=localhost
elasticsearch.port=9200
elasticsearch.index=foodtour_products_chatbot
elasticsearch.search-index=foodtour_products_search

# Ollama (for embeddings)
ollama.base-url=http://localhost:11434
ollama.embedding-model=nomic-embed-text

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## Monitoring

### Check Queue Length
```bash
redis-cli LLEN es:chatbot:sync:queue
redis-cli LLEN es:search:sync:queue
```

### View Events
```bash
redis-cli LRANGE es:chatbot:sync:queue 0 10
```

### Clear Queue (if needed)
```bash
redis-cli DEL es:chatbot:sync:queue
```

## Migration from Python Script

**Old code (REMOVED)**:
```java
// ❌ Old way - blocking Python call
@Autowired
private FoodVectorService foodVectorService;

foodVectorService.syncProductToEs(productId);  // Calls Python script
```

**New code (CURRENT)**:
```java
// ✅ New way - async Redis queue
@Autowired
private EsChatbotSyncProducer esChatbotSyncProducer;

esChatbotSyncProducer.pushIndexEvent(productId);  // Non-blocking
```

## Python Script Status

### ❌ Incremental Sync (Product thay đổi hàng ngày)
- **KHÔNG CẦN chạy Python scripts nữa**
- Java background consumers tự động sync mỗi 5s

### ✅ Full Reindex (Sync toàn bộ DB → ES)
- **VẪN CẦN Python scripts** cho các trường hợp:
  - ES index bị xóa hoàn toàn
  - Change ES mapping (thêm field mới, đổi analyzer)
  - Migration sang server mới
  - Data corruption trong ES

**Scripts:**
- `scripts/chatbot/sync_es_chatbot.py` - Full reindex cho chatbot index
- `scripts/search/sync_es_search.py` - Full reindex cho search index

**Cách chạy:**
```bash
# Reindex chatbot (with embeddings)
cd scripts/chatbot
python sync_es_chatbot.py

# Reindex search (no embeddings)
cd scripts/search
python sync_es_search.py
```

**Lý do giữ lại:**
- Redis queue chỉ có events GẦN ĐÂY (vài giờ/ngày)
- Không có toàn bộ products trong DB
- Scripts query ALL products từ MySQL → sync lên ES

## Testing

### Test Incremental Sync (Java Consumers)

### Test Chatbot Sync
1. Create/update a Product
2. Check Redis queue: `redis-cli LLEN es:chatbot:sync:queue`
3. Wait 5 seconds for consumer
4. Query ES: `curl localhost:9200/foodtour_products_chatbot/_doc/{productId}`
5. Verify `embedding` field has 768 dimensions

### Test Search Sync
1. Create/update a Product
2. Check Redis queue: `redis-cli LLEN es:search:sync:queue`
3. Wait 5 seconds for consumer
4. Query ES: `curl localhost:9200/foodtour_products_search/_doc/{productId}`

### Test Full Reindex (Python Scripts)

**When to use:**
- First time setup (empty ES indexes)
- ES data deleted/corrupted
- After changing ES mapping

**Chatbot Full Reindex:**
```bash
cd scripts/chatbot
python sync_es_chatbot.py
# Sync ALL products with embeddings to foodtour_products_chatbot
```

**Search Full Reindex:**
```bash
cd scripts/search
python sync_es_search.py
# Sync ALL products to foodtour_products_search
```

**Expected output:**
```
Synced 500 products to Elasticsearch
✓ Products with images: 450
✓ Embeddings generated: 450
```

## Troubleshooting

### Queue not processing?
- Check if consumers are running: Look for "EsChatbotSyncConsumer initialized" in logs
- Verify Ollama is running: `curl http://localhost:11434`
- Verify ES is running: `curl http://localhost:9200`

### Embedding generation slow?
- Ollama generates embeddings (~100ms per product)
- Batch size of 50 = ~5 seconds per batch
- Adjust `BATCH_SIZE` in consumer if needed

### Out of memory?
- Reduce batch size: `BATCH_SIZE = 20`
- Increase poll interval: `POLL_INTERVAL_MS = 10000`

---

# Part 2: Rate Limiting Service

## Overview

Redis-based rate limiting để bảo vệ auth endpoints khỏi abuse (brute force, spam).

## Architecture

**Key Pattern**: `rate:limit:{identifier}:{endpoint}`
- **identifier**: userId, IP, phone, email
- **endpoint**: "login", "register", "otp", "forgot_password"

**TTL**: Tự động expire sau time window (VD: 60s, 3600s)

## Usage

### 1. Basic Rate Limiting

```java
@Autowired
private RateLimitService rateLimitService;

// Example: Login - max 5 attempts per 15 minutes
String identifier = request.getEmail();  // or IP address
boolean allowed = rateLimitService.isAllowed(
    identifier, 
    "login", 
    5,      // maxRequests
    900     // windowSeconds (15 minutes)
);

if (!allowed) {
    long timeUntilReset = rateLimitService.getTimeUntilReset(identifier, "login");
    throw new RuntimeException(
        "Too many login attempts. Try again in " + timeUntilReset + " seconds"
    );
}

// Continue with login logic...
```

### 2. OTP Rate Limiting

```java
// Example: OTP - max 3 requests per 60 seconds
String phone = request.getPhone();
boolean allowed = rateLimitService.isAllowed(phone, "otp", 3, 60);

if (!allowed) {
    throw new RuntimeException("Too many OTP requests. Wait 1 minute.");
}

// Send OTP...
```

### 3. Registration Rate Limiting

```java
// Example: Register - max 10 per IP per hour
String ipAddress = getClientIP(request);
boolean allowed = rateLimitService.isAllowed(ipAddress, "register", 10, 3600);

if (!allowed) {
    throw new RuntimeException("Too many registrations from this IP. Try later.");
}

// Continue registration...
```

## API Methods

### isAllowed()
Check if request is within rate limit:
```java
boolean isAllowed(String identifier, String endpoint, int maxRequests, int windowSeconds)
```

### getRemainingRequests()
Get remaining requests in current window:
```java
int remaining = rateLimitService.getRemainingRequests(identifier, endpoint, maxRequests);
// Returns: remaining count, or -1 on error
```

### reset()
Manually reset rate limit (admin action):
```java
rateLimitService.reset(identifier, endpoint);
```

### getTimeUntilReset()
Get seconds until limit resets:
```java
long seconds = rateLimitService.getTimeUntilReset(identifier, endpoint);
```

## Recommended Limits

| Endpoint | Identifier | Max Requests | Time Window | Reason |
|----------|-----------|--------------|-------------|--------|
| **login** | email | 5 | 15 min | Prevent brute force |
| **register** | IP address | 10 | 1 hour | Prevent spam accounts |
| **otp_send** | phone | 3 | 60 sec | Prevent SMS abuse |
| **forgot_password** | email | 3 | 1 hour | Prevent email spam |
| **verify_otp** | phone | 5 | 5 min | Prevent OTP brute force |

## Implementation Example

### Add to AuthController

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @Autowired
    private RateLimitService rateLimitService;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        // Rate limit: 5 attempts per 15 minutes per email
        if (!rateLimitService.isAllowed(request.getEmail(), "login", 5, 900)) {
            long timeLeft = rateLimitService.getTimeUntilReset(request.getEmail(), "login");
            return ResponseEntity.status(429).body(
                Map.of("error", "Too many login attempts. Try again in " + timeLeft + "s")
            );
        }
        
        // Continue with authentication...
    }
    
    @PostMapping("/send-otp")
    public ResponseEntity<?> sendOTP(@RequestBody OTPRequest request) {
        // Rate limit: 3 OTP per 60 seconds per phone
        if (!rateLimitService.isAllowed(request.getPhone(), "otp", 3, 60)) {
            return ResponseEntity.status(429).body(
                Map.of("error", "Too many OTP requests. Wait 1 minute.")
            );
        }
        
        // Send OTP...
    }
}
```

## Monitoring

### Check Rate Limit Status
```bash
# Check current count for identifier
redis-cli GET "rate:limit:user@example.com:login"

# Check TTL
redis-cli TTL "rate:limit:user@example.com:login"

# View all rate limit keys
redis-cli KEYS "rate:limit:*"
```

### Reset User's Limit (Admin)
```bash
redis-cli DEL "rate:limit:user@example.com:login"
```

## Error Handling

Service **fails open** - nếu Redis lỗi, request vẫn được phép (availability > security):
```java
try {
    // Check rate limit via Redis
} catch (Exception e) {
    log.error("Redis error: {}", e.getMessage());
    return true;  // Allow request
}
```

## Benefits

✅ **Simple**: Chỉ cần 1 Redis INCR command  
✅ **Auto-cleanup**: TTL tự động xóa old keys  
✅ **Fast**: O(1) complexity  
✅ **Distributed**: Works across multiple server instances  
✅ **Fail-safe**: Degrades gracefully if Redis down  

---

# Part 3: Token Blacklist

**File**: `TokenBlacklistService.java`

**Key Pattern**: `token:blacklist:{token}`  
**TTL**: Matches JWT expiration time

**Usage**:
```java
// Add token to blacklist on logout
tokenBlacklistService.blacklistToken(token, expirationTime);

// Check if token is blacklisted (in JwtAuthenticationFilter)
if (tokenBlacklistService.isBlacklisted(token)) {
    throw new UnauthorizedException("Token has been revoked");
}
```

**Benefits**: No DB queries per request, auto-eviction via Redis TTL
