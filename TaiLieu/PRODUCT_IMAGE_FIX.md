# Cập Nhật Hiển Thị Ảnh Sản Phẩm

## Vấn đề
Ảnh sản phẩm không hiển thị trên Android Studio vì các DTO liên quan đến sản phẩm thiếu trường `imageUrls`.

## Giải pháp đã thực hiện

### 1. DTO đã được cập nhật

#### ✅ CartItemResponseDTO
- **Thêm trường**: `List<String> imageUrls`
- **Vị trí**: Sau trường `productName`
- **Mục đích**: Hiển thị ảnh sản phẩm trong giỏ hàng

#### ✅ OrderItemResponseDTO  
- **Thêm trường**: `List<String> imageUrls`
- **Vị trí**: Sau trường `productName`
- **Mục đích**: Hiển thị ảnh sản phẩm trong đơn hàng

#### ✅ ReviewResponse
- **Thêm trường**: `List<String> reviewableImageUrls`
- **Vị trí**: Sau trường `reviewableName`
- **Mục đích**: Hiển thị ảnh sản phẩm/shop được đánh giá

#### ✅ WishlistItemDTO
- **Đã có sẵn**: `List<String> imageUrls`
- **Không cần sửa**

#### ✅ ProductResponseDTO
- **Đã có sẵn**: `List<String> imageUrls`
- **Không cần sửa**

### 2. Service Implementation đã được cập nhật

#### CartServiceImpl
```java
private CartItemResponseDTO mapToCartItemResponseDTO(CartItem cartItem) {
    // ...
    
    // Map imageUrls from Product
    if (cartItem.getProduct().getImageUrls() != null && !cartItem.getProduct().getImageUrls().isEmpty()) {
        dto.setImageUrls(Arrays.asList(cartItem.getProduct().getImageUrls().split(",")));
    } else {
        dto.setImageUrls(List.of());
    }
    
    // ...
}
```

#### OrderServiceImpl
```java
private OrderItemResponseDTO mapToOrderItemResponseDTO(OrderItem orderItem) {
    // ...
    
    // Map imageUrls from Product
    if (orderItem.getProduct().getImageUrls() != null && !orderItem.getProduct().getImageUrls().isEmpty()) {
        dto.setImageUrls(Arrays.asList(orderItem.getProduct().getImageUrls().split(",")));
    } else {
        dto.setImageUrls(List.of());
    }
    
    // ...
}
```

#### ReviewServiceImpl
```java
private ReviewResponse mapToResponse(Review review) {
    // ...
    
    // Lấy tên shop/product và ảnh
    response.setReviewableName(getReviewableName(review.getReviewableType(), review.getReviewableId()));
    response.setReviewableImageUrls(getReviewableImageUrls(review.getReviewableType(), review.getReviewableId()));
    
    // ...
}

private List<String> getReviewableImageUrls(Review.ReviewableType type, Integer id) {
    try {
        if (type == Review.ReviewableType.shop) {
            return shopRepository.findById(id)
                    .map(shop -> {
                        if (shop.getLogoUrl() != null && !shop.getLogoUrl().isEmpty()) {
                            return List.of(shop.getLogoUrl());
                        }
                        return List.<String>of();
                    })
                    .orElse(List.of());
        } else {
            return productRepository.findById(id)
                    .map(product -> {
                        if (product.getImageUrls() != null && !product.getImageUrls().isEmpty()) {
                            return Arrays.asList(product.getImageUrls().split(","));
                        }
                        return List.<String>of();
                    })
                    .orElse(List.of());
        }
    } catch (Exception e) {
        log.error("Error getting reviewable image URLs: {}", e.getMessage());
        return List.of();
    }
}
```

## Cấu trúc dữ liệu

### Trong Database (Product entity)
```java
@Column(name = "image_urls", columnDefinition = "TEXT")
private String imageUrls; // Lưu dạng: "url1,url2,url3"
```

### Trong API Response
```json
{
  "imageUrls": [
    "http://domain.com/uploads/ProductImage/shop_1/image1.jpg",
    "http://domain.com/uploads/ProductImage/shop_1/image2.jpg",
    "http://domain.com/uploads/ProductImage/shop_1/image3.jpg"
  ]
}
```

## API Endpoints có ảnh sản phẩm

### 1. Cart APIs
- `GET /api/cart` - Lấy giỏ hàng
  - Response: `List<CartItemResponseDTO>` với `imageUrls`

- `POST /api/cart` - Thêm vào giỏ hàng
  - Response: `CartItemResponseDTO` với `imageUrls`

- `PUT /api/cart/{cartItemId}` - Cập nhật giỏ hàng
  - Response: `CartItemResponseDTO` với `imageUrls`

### 2. Order APIs
- `GET /api/orders` - Lấy danh sách đơn hàng
  - Response: `Page<OrderResponseDTO>` 
  - Mỗi order có `orderItems` với `imageUrls`

- `GET /api/orders/{orderId}` - Chi tiết đơn hàng
  - Response: `OrderResponseDTO` 
  - Có `orderItems` với `imageUrls`

- `POST /api/orders` - Tạo đơn hàng
  - Response: `OrderResponseDTO` 
  - Có `orderItems` với `imageUrls`

### 3. Wishlist APIs
- `GET /api/wishlist` - Lấy danh sách yêu thích
  - Response: `List<WishlistItemDTO>` với `imageUrls`

### 4. Product APIs
- `GET /api/products` - Danh sách sản phẩm
  - Response: `Page<ProductResponseDTO>` với `imageUrls`

- `GET /api/products/{id}` - Chi tiết sản phẩm
  - Response: `ProductResponseDTO` với `imageUrls`

### 5. Review APIs
- `GET /api/reviews` - Danh sách đánh giá
  - Response: `Page<ReviewResponse>` với `reviewableImageUrls`

- `GET /api/reviews/my` - Đánh giá của tôi
  - Response: `Page<ReviewResponse>` với `reviewableImageUrls`

## Hướng dẫn sử dụng trong Android

### 1. Model class trong Android
```kotlin
data class CartItemResponse(
    val id: Int,
    val productId: Int,
    val productName: String,
    val imageUrls: List<String>, // ← Thêm field này
    val unitPrice: BigDecimal,
    val quantity: Int,
    val totalPrice: BigDecimal,
    // ... other fields
)
```

### 2. Hiển thị ảnh đầu tiên
```kotlin
// Lấy ảnh đầu tiên hoặc ảnh mặc định
val imageUrl = cartItem.imageUrls.firstOrNull() ?: "default_image_url"

// Load bằng Glide
Glide.with(context)
    .load(imageUrl)
    .placeholder(R.drawable.placeholder)
    .error(R.drawable.error_image)
    .into(imageView)
```

### 3. Hiển thị nhiều ảnh (Gallery)
```kotlin
// Adapter cho RecyclerView hiển thị nhiều ảnh
class ProductImageAdapter(private val images: List<String>) : 
    RecyclerView.Adapter<ProductImageAdapter.ViewHolder>() {
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(holder.itemView.context)
            .load(images[position])
            .into(holder.imageView)
    }
    // ...
}
```

## Testing

### 1. Test với Postman
```bash
# Lấy giỏ hàng
GET http://localhost:8080/api/cart
Authorization: Bearer <your_token>

# Response sẽ có:
{
  "id": 1,
  "productId": 10,
  "productName": "Phở Bò",
  "imageUrls": [
    "http://localhost:8080/uploads/ProductImage/shop_1/pho-bo-1.jpg",
    "http://localhost:8080/uploads/ProductImage/shop_1/pho-bo-2.jpg"
  ],
  "unitPrice": 50000,
  "quantity": 2,
  ...
}
```

### 2. Kiểm tra ảnh có tồn tại
- Mở URL ảnh trên trình duyệt
- Ví dụ: `http://localhost:8080/uploads/ProductImage/shop_1/pho-bo-1.jpg`
- Nếu không hiển thị → Kiểm tra file có tồn tại trong thư mục `uploads/` không

## Xử lý lỗi thường gặp

### 1. Ảnh không hiển thị trong Android
**Nguyên nhân**: 
- URL không đúng format
- Backend chưa configure CORS
- Android không có quyền INTERNET

**Giải pháp**:
```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

### 2. imageUrls = null hoặc empty
**Nguyên nhân**: 
- Sản phẩm chưa có ảnh trong database
- Field `image_urls` trong DB là NULL

**Giải pháp**: 
- API trả về `[]` thay vì `null`
- Android check: `if (imageUrls.isNullOrEmpty())`

### 3. Ảnh bị lỗi 404
**Nguyên nhân**:
- File đã bị xóa
- Path không đúng

**Giải pháp**:
- Sử dụng placeholder/error image trong Glide
- Log URL để debug

## Lưu ý quan trọng

1. **Format URL trong Database**: `"url1,url2,url3"` (phân cách bởi dấu phẩy)
2. **Format URL trong Response**: `["url1", "url2", "url3"]` (Array/List)
3. **Backend tự động split**: Dùng `split(",")` để chuyển từ String sang List
4. **Luôn check null**: Trước khi split hoặc access list
5. **Dùng ảnh đầu tiên**: Cho thumbnail/preview
6. **Dùng tất cả ảnh**: Cho gallery/detail view

## Build lại project

```bash
# Build backend
mvn clean install

# Hoặc chỉ compile
mvn clean compile

# Chạy lại server
mvn spring-boot:run
```

## Đã hoàn thành ✅

- [x] Thêm `imageUrls` vào CartItemResponseDTO
- [x] Thêm `imageUrls` vào OrderItemResponseDTO  
- [x] Thêm `reviewableImageUrls` vào ReviewResponse
- [x] Cập nhật CartServiceImpl mapping
- [x] Cập nhật OrderServiceImpl mapping
- [x] Cập nhật ReviewServiceImpl mapping
- [x] Kiểm tra không có lỗi compile

## Bước tiếp theo cho Android Developer

1. Cập nhật model class trong Android thêm field `imageUrls`
2. Parse JSON response đúng cách
3. Sử dụng Glide/Picasso để load ảnh
4. Test với data thật từ API
5. Xử lý trường hợp ảnh null/empty

---
**Ngày tạo**: 31/01/2026  
**Người tạo**: Backend Developer  
**Trạng thái**: ✅ Hoàn thành

