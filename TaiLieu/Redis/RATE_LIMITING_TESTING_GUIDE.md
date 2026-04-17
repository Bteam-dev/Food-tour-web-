# Rate Limiting - Testing Guide

## Overview

Rate limiting đã được áp dụng cho các auth endpoints để bảo vệ khỏi brute force và spam attacks.

## Endpoints với Rate Limiting

| Endpoint | Identifier | Max Requests | Time Window | Purpose |
|----------|-----------|--------------|-------------|---------|
| `POST /api/auth/login` | username | 5 | 15 minutes | Prevent brute force password attacks |
| `POST /api/auth/verify-2fa` | email | 5 | 5 minutes | Prevent 2FA code brute force |
| `POST /api/auth/register` | IP address | 10 | 1 hour | Prevent mass account creation |
| `POST /api/auth/forgot-password` | email | 3 | 1 hour | Prevent email spam |
| `POST /api/auth/verify-otp` | email | 5 | 5 minutes | Prevent OTP brute force |

---

## Rate Limit Response Format

Khi vượt rate limit, server trả về **HTTP 429 Too Many Requests**:

```json
{
  "success": false,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many login attempts. Please try again in 12 minutes.",
  "retryAfterSeconds": 720
}
```

**Fields:**
- `error`: Error code để client xử lý
- `message`: Human-readable message (tiếng Việt/English)
- `retryAfterSeconds`: Số giây còn lại cho đến khi reset

---

## Testing với cURL

### 1. Test Login Rate Limit (5 attempts per 15 min)

```bash
# Attempt 1-5: Should succeed (or fail with auth error)
for i in {1..5}; do
  echo "Attempt $i:"
  curl -X POST http://localhost:8080/api/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "username": "testuser@example.com",
      "password": "wrongpassword"
    }'
  echo -e "\n"
done

# Attempt 6: Should return 429 Rate Limit Exceeded
echo "Attempt 6 (should fail with rate limit):"
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser@example.com",
    "password": "wrongpassword"
  }' \
  -w "\nHTTP Status: %{http_code}\n"
```

**Expected Output:**
```json
{
  "success": false,
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "Too many login attempts. Please try again in 15 minutes.",
  "retryAfterSeconds": 900
}
```

---

### 2. Test Register Rate Limit (10 per hour per IP)

```bash
# Attempt 1-10: Should work
for i in {1..10}; do
  echo "Registration attempt $i:"
  curl -X POST http://localhost:8080/api/auth/register \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"user$i@example.com\",
      \"password\": \"Password123!\",
      \"fullName\": \"Test User $i\",
      \"phone\": \"098765432$i\"
    }"
  echo -e "\n"
done

# Attempt 11: Should return 429
echo "Registration attempt 11 (should fail):"
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "user11@example.com",
    "password": "Password123!",
    "fullName": "Test User 11",
    "phone": "0987654321"
  }' \
  -w "\nHTTP Status: %{http_code}\n"
```

---

### 3. Test Forgot Password Rate Limit (3 per hour)

```bash
# Attempt 1-3: Should work
for i in {1..3}; do
  echo "Forgot password attempt $i:"
  curl -X POST http://localhost:8080/api/auth/forgot-password \
    -H "Content-Type: application/json" \
    -d '{
      "email": "testuser@example.com"
    }'
  echo -e "\n"
  sleep 1
done

# Attempt 4: Should return 429
echo "Forgot password attempt 4 (should fail):"
curl -X POST http://localhost:8080/api/auth/forgot-password \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com"
  }' \
  -w "\nHTTP Status: %{http_code}\n"
```

---

### 4. Test OTP Verification Rate Limit (5 per 5 min)

```bash
# Attempt 1-5: Should work (will fail with wrong OTP)
for i in {1..5}; do
  echo "OTP verification attempt $i:"
  curl -X POST http://localhost:8080/api/auth/verify-otp \
    -H "Content-Type: application/json" \
    -d '{
      "email": "testuser@example.com",
      "otp": "123456"
    }'
  echo -e "\n"
done

# Attempt 6: Should return 429
echo "OTP verification attempt 6 (should fail with rate limit):"
curl -X POST http://localhost:8080/api/auth/verify-otp \
  -H "Content-Type: application/json" \
  -d '{
    "email": "testuser@example.com",
    "otp": "123456"
  }' \
  -w "\nHTTP Status: %{http_code}\n"
```

---

## Testing với Postman

### Setup Collection

1. **Import Collection**: Create new collection "Auth Rate Limiting Tests"

2. **Create Environment Variables**:
   - `BASE_URL`: `http://localhost:8080`
   - `TEST_EMAIL`: `testuser@example.com`
   - `TEST_USERNAME`: `testuser`

### Test Cases

#### Test 1: Login Rate Limit
```
Method: POST
URL: {{BASE_URL}}/api/auth/login
Body (JSON):
{
  "username": "{{TEST_USERNAME}}",
  "password": "wrongpassword"
}

Test Script:
// Run 6 times
if (pm.info.iteration === 5) {
  pm.test("Should return 429 on 6th attempt", function() {
    pm.response.to.have.status(429);
  });
  
  pm.test("Response has error code", function() {
    var jsonData = pm.response.json();
    pm.expect(jsonData.error).to.eql("RATE_LIMIT_EXCEEDED");
  });
}
```

#### Test 2: Register Rate Limit
```
Method: POST
URL: {{BASE_URL}}/api/auth/register
Body (JSON):
{
  "username": "user{{$randomInt}}@example.com",
  "password": "Password123!",
  "fullName": "Test User",
  "phone": "0987654321"
}

Test Script:
// Run 11 times
if (pm.info.iteration === 10) {
  pm.test("Should return 429 on 11th attempt", function() {
    pm.response.to.have.status(429);
  });
}
```

---

## Monitoring Rate Limits

### Check Redis Keys

```bash
# View all rate limit keys
redis-cli KEYS "rate:limit:*"

# Check specific user's login attempts
redis-cli GET "rate:limit:testuser@example.com:login"

# Check TTL (time to live)
redis-cli TTL "rate:limit:testuser@example.com:login"

# Check registration attempts from IP
redis-cli GET "rate:limit:192.168.1.100:register"
```

**Example Output:**
```bash
$ redis-cli GET "rate:limit:testuser@example.com:login"
"5"

$ redis-cli TTL "rate:limit:testuser@example.com:login"
(integer) 847  # 847 seconds remaining
```

---

## Admin: Reset Rate Limits

### Reset Specific User
```bash
# Reset login attempts for user
redis-cli DEL "rate:limit:testuser@example.com:login"

# Reset forgot-password for email
redis-cli DEL "rate:limit:testuser@example.com:forgot-password"
```

### Reset All Rate Limits for User
```bash
# Find all keys for user
redis-cli KEYS "rate:limit:testuser@example.com:*"

# Delete all
redis-cli DEL $(redis-cli KEYS "rate:limit:testuser@example.com:*")
```

### Reset by Endpoint (all users)
```bash
# Careful! This resets for ALL users
redis-cli KEYS "rate:limit:*:login" | xargs redis-cli DEL
```

---

## Client-Side Handling

### JavaScript/TypeScript Example

```typescript
async function login(username: string, password: string) {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
    });
    
    if (response.status === 429) {
      const data = await response.json();
      const minutes = Math.ceil(data.retryAfterSeconds / 60);
      
      // Show user-friendly error
      alert(`Too many login attempts. Please try again in ${minutes} minutes.`);
      
      // Optional: Disable login button and show countdown
      disableLoginButton(data.retryAfterSeconds);
      return;
    }
    
    if (!response.ok) {
      throw new Error('Login failed');
    }
    
    const data = await response.json();
    // Handle successful login
    
  } catch (error) {
    console.error('Login error:', error);
  }
}

function disableLoginButton(seconds: number) {
  const button = document.getElementById('login-btn');
  button.disabled = true;
  
  const interval = setInterval(() => {
    seconds--;
    button.textContent = `Try again in ${Math.ceil(seconds / 60)} minutes`;
    
    if (seconds <= 0) {
      clearInterval(interval);
      button.disabled = false;
      button.textContent = 'Login';
    }
  }, 1000);
}
```

### React Example

```typescript
import { useState } from 'react';

function LoginForm() {
  const [error, setError] = useState<string | null>(null);
  const [retryAfter, setRetryAfter] = useState<number>(0);
  
  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    
    try {
      const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, password })
      });
      
      if (response.status === 429) {
        const data = await response.json();
        setError(data.message);
        setRetryAfter(data.retryAfterSeconds);
        
        // Start countdown
        const timer = setInterval(() => {
          setRetryAfter(prev => {
            if (prev <= 1) {
              clearInterval(timer);
              setError(null);
              return 0;
            }
            return prev - 1;
          });
        }, 1000);
        
        return;
      }
      
      // Handle success...
      
    } catch (error) {
      setError('Login failed');
    }
  };
  
  return (
    <form onSubmit={handleLogin}>
      {error && <div className="error">{error}</div>}
      
      <input type="text" name="username" />
      <input type="password" name="password" />
      
      <button 
        type="submit" 
        disabled={retryAfter > 0}
      >
        {retryAfter > 0 
          ? `Try again in ${Math.ceil(retryAfter / 60)}m` 
          : 'Login'
        }
      </button>
    </form>
  );
}
```

---

## Troubleshooting

### Issue: Rate limit not working

**Check:**
1. Redis is running: `redis-cli ping` → Should return `PONG`
2. Spring Boot connected to Redis: Check logs for "Connected to Redis"
3. RateLimitService bean loaded: `grep "RateLimitServiceImpl" logs/app.log`

### Issue: Too strict / Too lenient

**Adjust limits** in `PublicAuthController.java`:
```java
// Current: 5 attempts per 15 minutes
rateLimitService.isAllowed(identifier, "login", 5, 900)

// More lenient: 10 attempts per 30 minutes
rateLimitService.isAllowed(identifier, "login", 10, 1800)

// Stricter: 3 attempts per 10 minutes
rateLimitService.isAllowed(identifier, "login", 3, 600)
```

### Issue: Different IPs for same user

**Problem**: Load balancer/proxy changes IP  
**Solution**: Already handled via `X-Forwarded-For` header:
```java
private String getClientIP(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null) {
        return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
}
```

### Issue: Rate limit persists after time window

**Cause**: Redis TTL not set properly  
**Fix**: 
```bash
# Check TTL
redis-cli TTL "rate:limit:user@example.com:login"

# If returns -1 (no expiry), manually delete
redis-cli DEL "rate:limit:user@example.com:login"
```

---

## Performance Impact

**Redis Performance:**
- Each rate limit check: **~1ms** (INCR + TTL commands)
- Minimal overhead compared to auth logic (password hashing ~100ms)

**Recommendations:**
- ✅ Always use rate limiting for public auth endpoints
- ✅ Monitor Redis memory usage: `redis-cli INFO memory`
- ✅ Set max memory policy: `maxmemory-policy allkeys-lru`

---

## Security Best Practices

1. **Use different identifiers:**
   - Login: username/email (prevents account enumeration)
   - Register: IP address (prevents mass signup)
   - OTP: email (prevents brute force)

2. **Combine with other security measures:**
   - CAPTCHA after 3 failed attempts
   - Account lockout after 10 failed attempts
   - Email notification on suspicious activity

3. **Monitor and alert:**
   - Log rate limit violations
   - Alert admins on repeated violations
   - Consider IP blocking for extreme cases

4. **Fail-safe behavior:**
   - If Redis is down, allow requests (availability > strict security)
   - Log errors for investigation
   - Consider circuit breaker pattern

---

## Summary

**Rate limiting applied to:**
- ✅ Login (5 per 15 min)
- ✅ 2FA verification (5 per 5 min)
- ✅ Registration (10 per hour per IP)
- ✅ Forgot password (3 per hour)
- ✅ OTP verification (5 per 5 min)

**Response format:** HTTP 429 with `retryAfterSeconds`

**Testing:** Use cURL, Postman, or automated scripts

**Monitoring:** Redis CLI commands

**Admin tools:** Reset via Redis DEL commands
