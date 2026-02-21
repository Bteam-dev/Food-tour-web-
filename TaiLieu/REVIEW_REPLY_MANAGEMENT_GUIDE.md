# HƯỚNG DẪN QUẢN LÝ REPLY REVIEW

## 📋 TỔNG QUAN

Hệ thống review đã được cải tiến với đầy đủ các chức năng:

### ✅ Cơ chế Review mới:
- **Review theo ORDER**: Mỗi đơn hàng chỉ review 1 lần cho mỗi sản phẩm
- **Cho phép review cùng sản phẩm nhiều lần**: Nếu mua ở các đơn hàng khác nhau
- **VD**: Đặt sản phẩm A hôm nay → review được. Đặt sản phẩm A ngày mai → vẫn review được!

### ✅ Cơ chế Reply mới:
- **Conversation Thread**: Reply được KHÔNG GIỚI HẠN số lần
- **Cả shop owner VÀ user đều reply được**: Ai cũng có quyền phản biện!
- **Có ảnh kèm theo**: Mỗi reply có thể đính kèm ảnh (multipart/form-data)
- **Quản lý đầy đủ**: Xem, sửa, xóa reply của mình

---

## 🔥 API ENDPOINTS - REPLY MANAGEMENT

### 1️⃣ TẠO REPLY MỚI (Trả lời review) - USER PROTECTED
**Endpoint**: `POST /api/user/reviews/{reviewId}/reply`

**Authorization**: Bearer Token (USER, SELLER, ADMIN)

**Quyền trả lời**:
- ✅ Shop owner (chủ shop bị review)
- ✅ User (người đã viết review - để phản bác lại)

**Request Type**: `multipart/form-data`

**Parameters**:
```
Path Variables:
- reviewId: Integer (ID của review cần reply)

Form Data Parts:
1. data (application/json) - REQUIRED:
   {
     "reply": "Nội dung trả lời" // Max 500 ký tự
   }

2. images (multipart/file) - OPTIONAL:
   - Cho phép nhiều files
   - Mỗi file tối đa 5MB
   - Định dạng: JPG, PNG, JPEG
```

**Example Request (Postman)**:
```
POST http://localhost:8080/api/user/reviews/123/reply

Headers:
Authorization: Bearer {your_token}

Body (form-data):
- Key: data
  Type: Text
  Value: {"reply":"Cảm ơn bạn đã mua hàng! Sản phẩm của shop luôn đảm bảo chất lượng."}

- Key: images
  Type: File
  Value: [Select file 1]

- Key: images
  Type: File
  Value: [Select file 2]
```

**Response Success** (200 OK):
```json
{
  "success": true,
  "message": "Đã trả lời đánh giá",
  "data": {
    "id": 123,
    "userId": 1,
    "userFullName": "Nguyen Van A",
    "rating": 5,
    "comment": "Sản phẩm tuyệt vời!",
    "images": ["url1", "url2"],
    "replies": [
      {
        "id": 1,
        "reviewId": 123,
        "userId": 10,
        "userFullName": "Shop Owner",
        "userAvatarUrl": "avatar.jpg",
        "replyText": "Cảm ơn bạn đã mua hàng!",
        "images": ["reply_img1.jpg", "reply_img2.jpg"],
        "replyType": "SHOP_OWNER",
        "createdAt": "2024-01-01T10:00:00",
        "updatedAt": "2024-01-01T10:00:00"
      }
    ]
  }
}
```

**Response Error** (400 Bad Request):
```json
{
  "success": false,
  "message": "Bạn không có quyền reply review này. Chỉ chủ shop hoặc người đã review mới có thể reply."
}
```

---

### 2️⃣ XEM DANH SÁCH REPLY CỦA 1 REVIEW - PUBLIC
**Endpoint**: `GET /api/public/reviews/{reviewId}/replies`

**Authorization**: KHÔNG CẦN (Public - ai cũng xem được)

**Parameters**:
```
Path Variables:
- reviewId: Integer (ID của review)
```

**Example Request**:
```
GET http://localhost:8080/api/public/reviews/123/replies
```

**Response Success** (200 OK):
```json
{
  "success": true,
  "data": [
    {
      "id": 1,
      "reviewId": 123,
      "userId": 10,
      "userFullName": "Shop Owner",
      "userAvatarUrl": "avatar.jpg",
      "replyText": "Cảm ơn bạn đã mua hàng!",
      "images": ["img1.jpg", "img2.jpg"],
      "replyType": "SHOP_OWNER",
      "createdAt": "2024-01-01T10:00:00",
      "updatedAt": "2024-01-01T10:00:00"
    },
    {
      "id": 2,
      "reviewId": 123,
      "userId": 5,
      "userFullName": "Nguyen Van A",
      "userAvatarUrl": "user_avatar.jpg",
      "replyText": "Nhưng sản phẩm bị lỗi rồi bạn ơi!",
      "images": ["proof1.jpg"],
      "replyType": "USER",
      "createdAt": "2024-01-01T11:00:00",
      "updatedAt": "2024-01-01T11:00:00"
    },
    {
      "id": 3,
      "reviewId": 123,
      "userId": 10,
      "userFullName": "Shop Owner",
      "userAvatarUrl": "avatar.jpg",
      "replyText": "Bạn inbox shop để được hỗ trợ đổi trả nhé!",
      "images": [],
      "replyType": "SHOP_OWNER",
      "createdAt": "2024-01-01T12:00:00",
      "updatedAt": "2024-01-01T12:00:00"
    }
  ],
  "totalReplies": 3
}
```

**Giải thích**:
- Danh sách replies được sắp xếp theo thời gian tạo (cũ → mới)
- `replyType`: 
  - `SHOP_OWNER`: Reply từ chủ shop
  - `USER`: Reply từ người đã review (phản bác)
- **PUBLIC**: Không cần login, ai cũng xem được conversation thread

---

### 3️⃣ SỬA REPLY CỦA MÌNH - USER PROTECTED
**Endpoint**: `PUT /api/user/reviews/replies/{replyId}`

**Authorization**: Bearer Token (USER, SELLER, ADMIN)

**Quyền sửa**: Chỉ người tạo reply mới được sửa

**Request Type**: `multipart/form-data`

**Parameters**:
```
Path Variables:
- replyId: Integer (ID của reply cần sửa)

Form Data Parts:
1. data (application/json) - REQUIRED:
   {
     "reply": "Nội dung đã sửa"
   }

2. images (multipart/file) - OPTIONAL:
   - Nếu upload ảnh mới → ảnh cũ sẽ bị XÓA
   - Nếu không upload → giữ nguyên ảnh cũ
```

**Example Request (Postman)**:
```
PUT http://localhost:8080/api/user/reviews/replies/1

Headers:
Authorization: Bearer {your_token}

Body (form-data):
- Key: data
  Type: Text
  Value: {"reply":"Cảm ơn bạn! Shop sẽ hỗ trợ đổi trả ngay!"}

- Key: images
  Type: File
  Value: [Select new image]
```

**Response Success** (200 OK):
```json
{
  "success": true,
  "message": "Cập nhật reply thành công",
  "data": {
    "id": 1,
    "reviewId": 123,
    "userId": 10,
    "userFullName": "Shop Owner",
    "userAvatarUrl": "avatar.jpg",
    "replyText": "Cảm ơn bạn! Shop sẽ hỗ trợ đổi trả ngay!",
    "images": ["new_image.jpg"],
    "replyType": "SHOP_OWNER",
    "createdAt": "2024-01-01T10:00:00",
    "updatedAt": "2024-01-01T13:00:00"
  }
}
```

**Response Error** (400 Bad Request):
```json
{
  "success": false,
  "message": "Bạn không có quyền sửa reply này"
}
```

---

### 4️⃣ XÓA REPLY CỦA MÌNH - USER PROTECTED
**Endpoint**: `DELETE /api/user/reviews/replies/{replyId}`

**Authorization**: Bearer Token (USER, SELLER, ADMIN)

**Quyền xóa**: Chỉ người tạo reply mới được xóa

**Parameters**:
```
Path Variables:
- replyId: Integer (ID của reply cần xóa)
```

**Example Request**:
```
DELETE http://localhost:8080/api/user/reviews/replies/1

Headers:
Authorization: Bearer {your_token}
```

**Response Success** (200 OK):
```json
{
  "success": true,
  "message": "Xóa reply thành công"
}
```

**Response Error** (400 Bad Request):
```json
{
  "success": false,
  "message": "Bạn không có quyền xóa reply này"
}
```

**Lưu ý**: Khi xóa reply, tất cả ảnh kèm theo cũng sẽ bị xóa khỏi server.

---

## 📊 PUBLIC ENDPOINTS - XEM REVIEW (KHÔNG CẦN LOGIN)

### 1. Xem danh sách review của SHOP
```
GET /api/public/reviews/shops/{shopId}
?page=0&size=10&sortBy=createdAt&sortDir=DESC
```

### 2. Xem danh sách review của PRODUCT
```
GET /api/public/reviews/products/{productId}
?page=0&size=10&sortBy=createdAt&sortDir=DESC
```

### 3. Xem thống kê rating của SHOP
```
GET /api/public/reviews/shops/{shopId}/statistics
```

### 4. Xem thống kê rating của PRODUCT
```
GET /api/public/reviews/products/{productId}/statistics
```

### 5. Xem chi tiết 1 review (có đầy đủ replies)
```
GET /api/public/reviews/{reviewId}
```

### 6. Xem danh sách reply của 1 review
```
GET /api/public/reviews/{reviewId}/replies
```

**Lưu ý**: Tất cả endpoints trên đều PUBLIC (không cần login)

---

## 🎯 LUỒNG SỬ DỤNG THỰC TẾ

### Scenario 1: User review sản phẩm, Shop owner trả lời
```
1. User mua sản phẩm A từ Order #100
2. User review sản phẩm A (rating 3 sao): "Sản phẩm chất lượng TB"
3. Shop owner thấy review → Reply: "Cảm ơn bạn! Shop sẽ cải thiện!"
4. User xem reply → Thấy shop nhiệt tình → Sửa review thành 4 sao
```

### Scenario 2: User mua nhiều lần cùng sản phẩm
```
1. User mua sản phẩm A từ Order #100 (ngày 1/1)
   → Review: "Sản phẩm tốt!" (5 sao)

2. User mua lại sản phẩm A từ Order #200 (ngày 1/2)
   → Lần này nhận được hàng lỗi
   → Review: "Lần này bị lỗi rồi!" (2 sao)
   
✅ ĐƯỢC PHÉP! Vì 2 order khác nhau
```

### Scenario 3: Conversation thread dài (cãi nhau)
```
1. User review: "Hàng giả!"
2. Shop owner reply: "Hàng của shop 100% chính hãng!"
3. User reply: "Tôi có bằng chứng đây!" [Kèm ảnh]
4. Shop owner reply: "Bạn inbox shop để được hỗ trợ đổi trả!"
5. User reply: "OK, cảm ơn shop!"

✅ Tất cả đều được lưu trong conversation thread
✅ Ai cũng xem được (public endpoint)
```

---

## 📊 CẤU TRÚC DỮ LIỆU

### Review Entity
```
- id: Integer
- user_id: Integer (người review)
- reviewable_type: ENUM('shop', 'product')
- reviewable_id: Integer (ID của shop hoặc product)
- order_id: Integer (NULL cho shop review)
- rating: Integer (1-5)
- comment: TEXT
- images: JSON Array
- is_anonymous: Boolean
- is_approved: Boolean
- created_at: DateTime
- updated_at: DateTime
```

### ReviewReply Entity
```
- id: Integer
- review_id: Integer (FK → reviews)
- user_id: Integer (FK → users)
- reply_text: TEXT
- images: JSON Array
- reply_type: ENUM('SHOP_OWNER', 'USER')
- created_at: DateTime
- updated_at: DateTime
```

---

## ⚠️ LƯU Ý QUAN TRỌNG

### 1. Logic Review theo ORDER
- ❌ **SAI**: "User chỉ review được 1 lần cho 1 sản phẩm"
- ✅ **ĐÚNG**: "User chỉ review được 1 lần cho 1 sản phẩm TRONG 1 ORDER"
- Nếu mua nhiều lần → Review được nhiều lần!

### 2. Quyền Reply
- ✅ Shop owner: LUÔN ĐƯỢC reply review của shop/product mình
- ✅ User đã review: LUÔN ĐƯỢC reply lại để phản bác
- ❌ User khác: KHÔNG ĐƯỢC reply vào review của người khác

### 3. Reply Unlimited
- Không giới hạn số lần reply
- Tất cả replies tạo thành conversation thread
- Sắp xếp theo thời gian (cũ → mới)

### 4. Upload Ảnh cho Reply
- Reply hỗ trợ upload ảnh (multipart/form-data)
- Ảnh được lưu vào thư mục: `ReviewImage/user_{userId}/`
- Khi sửa reply + upload ảnh mới → Ảnh cũ tự động XÓA
- Khi xóa reply → Ảnh cũng tự động XÓA

### 5. Public vs Protected Endpoints
- **Public** (`/api/public/reviews/**`):
  - Xem danh sách reviews
  - Xem chi tiết review
  - Xem danh sách replies
  - Xem statistics
  - KHÔNG CẦN LOGIN
  
- **Protected** (`/api/user/reviews/**`):
  - Tạo review
  - Sửa review
  - Xóa review
  - Tạo reply
  - Sửa reply
  - Xóa reply
  - CẦN LOGIN

### 6. Backward Compatible
- Fields cũ (`reply`, `userReply`) vẫn được giữ lại
- Nhưng recommend dùng `replies` array mới (conversation thread)

---

## 🔧 TESTING

### Test Case 1: Reply với ảnh (Protected)
```bash
curl -X POST http://localhost:8080/api/user/reviews/123/reply \
  -H "Authorization: Bearer {token}" \
  -F 'data={"reply":"Test reply with image"}' \
  -F 'images=@/path/to/image1.jpg' \
  -F 'images=@/path/to/image2.jpg'
```

### Test Case 2: Xem danh sách replies (Public - không cần token)
```bash
curl -X GET http://localhost:8080/api/public/reviews/123/replies
```

### Test Case 3: Sửa reply (Protected)
```bash
curl -X PUT http://localhost:8080/api/user/reviews/replies/1 \
  -H "Authorization: Bearer {token}" \
  -F 'data={"reply":"Updated reply text"}' \
  -F 'images=@/path/to/new_image.jpg'
```

### Test Case 4: Xóa reply (Protected)
```bash
curl -X DELETE http://localhost:8080/api/user/reviews/replies/1 \
  -H "Authorization: Bearer {token}"
```

---

## ✅ CHECKLIST TRIỂN KHAI

- [x] Review theo ORDER (không phải theo PRODUCT)
- [x] Cho phép reply không giới hạn
- [x] Cả shop owner VÀ user đều reply được
- [x] Reply có hỗ trợ ảnh
- [x] API xem danh sách replies (PUBLIC)
- [x] API sửa reply (PROTECTED)
- [x] API xóa reply (PROTECTED)
- [x] Kiểm tra quyền reply (shop owner hoặc review owner)
- [x] Kiểm tra quyền sửa/xóa reply (chỉ owner)
- [x] Auto xóa ảnh khi sửa/xóa reply
- [x] Tách biệt public và protected endpoints

---

## 📞 HỖ TRỢ

Nếu có vấn đề, kiểm tra log tại:
- Controller: `UserReviewController.java` (Protected endpoints)
- Controller: `PublicReviewController.java` (Public endpoints)
- Service: `ReviewServiceImpl.java`
- Log pattern: `log.info()` và `log.error()`
