# HƯỚNG DẪN: LUỒNG XỬ LÝ ĐON HÀNG MỚI

## Ngày cập nhật: 2026-02-09

---

## 1. TRẠNG THÁI ĐƠN HÀNG

### OrderStatus (4 trạng thái)
- **Pending**: Đơn hàng mới tạo, chờ shop xác nhận
- **Confirmed**: Shop đã xác nhận đơn hàng
- **Delivered**: Đơn hàng đã được giao thành công
- **Cancelled**: Đơn hàng bị hủy (bởi user hoặc shop)

### PaymentStatus (3 trạng thái)
- **Pending**: Chưa thanh toán
- **Paid**: Đã thanh toán
- **Refunded**: Đã hoàn tiền

---

## 2. LUỒNG XỬ LÝ ĐƠN HÀNG

### 2.1. Tạo Đơn Hàng (User)
```
POST /api/user/orders
```
- User chọn sản phẩm từ giỏ hàng
- Chọn phương thức thanh toán: `app_wallet` hoặc `ship_cod`
- Nhập địa chỉ giao hàng
- **Trạng thái ban đầu**: 
  - `orderStatus: pending`
  - `paymentStatus: pending`

### 2.2. Thanh Toán (User - chỉ với app_wallet)
```
POST /api/user/orders/{orderId}/pay
```
- User thanh toán qua ví app
- Tiền sẽ được trừ ngay từ ví user
- Tiền được phân phối:
  - 88% cho seller (sau khi trừ 12% hoa hồng platform)
  - 12% cho admin (hoa hồng platform)
- **Trạng thái sau thanh toán**:
  - `orderStatus: pending` (vẫn chờ shop confirm)
  - `paymentStatus: paid`

**Lưu ý**: Đơn COD không cần thanh toán ngay, sẽ thanh toán khi nhận hàng.

### 2.3. Shop Xác Nhận Đơn Hàng (Seller)
```
POST /api/seller/orders/{orderId}/confirm
```
- Shop kiểm tra đơn hàng và xác nhận
- Chỉ đơn hàng ở trạng thái `pending` mới có thể confirm
- **Trạng thái sau confirm**:
  - `orderStatus: confirmed`
  - `confirmedBy: seller_id`
  - `confirmedAt: timestamp`

### 2.4. Shop Đánh Dấu Đã Giao Hàng (Seller)
```
POST /api/seller/orders/{orderId}/deliver
```
- Shipper đã giao hàng thành công cho khách
- Chỉ đơn hàng ở trạng thái `confirmed` mới có thể đánh dấu delivered
- **Đặc biệt với COD**: 
  - Tự động xác nhận đã nhận tiền từ shipper
  - `paymentStatus` chuyển sang `paid`
- **Trạng thái sau deliver**:
  - `orderStatus: delivered`
  - `actualDeliveryTime: timestamp`
  - `paymentStatus: paid` (nếu là COD)

---

## 3. XỬ LÝ HỦY ĐƠN & HOÀN TIỀN

### 3.1. User Hủy Đơn (Chỉ khi chưa confirm)
```
DELETE /api/user/orders/{orderId}
```
- User chỉ có thể hủy khi đơn hàng đang ở trạng thái `pending`
- Nếu đã thanh toán qua ví → **Tự động hoàn tiền** về ví user
- **Trạng thái**:
  - `orderStatus: cancelled`
  - `cancelledBy: user_id`
  - `cancelledReason: "Cancelled by user"`

### 3.2. Shop Hủy Đơn (Khi cần thiết)
```
POST /api/seller/orders/{orderId}/cancel
Body: { "reason": "Lý do hủy" }
```
- Shop có thể hủy đơn khi user yêu cầu (ví dụ qua chat)
- Có thể hủy đơn ở trạng thái `pending` hoặc `confirmed`
- Không thể hủy đơn đã `delivered`
- Nếu đã thanh toán qua ví → **Tự động hoàn tiền** về ví user
- **Trạng thái**:
  - `orderStatus: cancelled`
  - `cancelledBy: seller_id`
  - `cancelledReason: reason from request`

### 3.3. Shop Hoàn Tiền (Cho đơn hàng có vấn đề)
```
POST /api/seller/orders/{orderId}/refund
```
- Shop quyết định hoàn tiền sau khi xem review/khiếu nại của user
- Chỉ hoàn tiền cho đơn đã thanh toán (`paymentStatus: paid`)
- **Cơ chế hoàn tiền**:
  - Tiền được hoàn về ví user
  - Tiền bị trừ từ ví seller
- **Trạng thái**:
  - `paymentStatus: refunded`
  - `orderStatus: không đổi` (có thể vẫn là delivered)

---

## 4. PHƯƠNG THỨC THANH TOÁN

### 4.1. App Wallet (Ví app)
**Luồng xử lý**:
1. User tạo đơn → `orderStatus: pending`, `paymentStatus: pending`
2. User thanh toán → Trừ tiền ví → `paymentStatus: paid`
3. Shop xác nhận → `orderStatus: confirmed`
4. Shop giao hàng → `orderStatus: delivered`

**Hủy/Hoàn tiền**:
- Hủy đơn → Tiền hoàn về ví user tự động
- Refund → Tiền hoàn về ví user, trừ từ ví seller

### 4.2. Ship COD (Tiền mặt khi nhận hàng)
**Luồng xử lý**:
1. User tạo đơn → `orderStatus: pending`, `paymentStatus: pending`
2. Shop xác nhận → `orderStatus: confirmed`
3. Shop giao hàng → Shipper báo đã giao và nhận tiền
4. Shop đánh dấu delivered → `orderStatus: delivered`, `paymentStatus: paid`

**Hủy**:
- Chỉ có thể hủy khi chưa giao hàng
- Không có hoàn tiền vì chưa thanh toán

**Refund COD**:
- Nếu cần refund đơn COD, seller phải tự xử lý ngoài hệ thống (trả tiền mặt cho user)

---

## 5. CƠ CHẾ REVIEW & REFUND

### Đánh giá đơn hàng (Review Order)
- User có thể review sau khi đơn hàng `delivered`
- Review bao gồm:
  - Rating (1-5 sao)
  - Comment (nhận xét)
  - Images (ảnh món ăn thực tế)
  - Yêu cầu refund (nếu có)

### Xử lý Refund Request
1. User review đơn hàng + gửi yêu cầu refund với lý do
2. Seller xem danh sách đơn hàng có yêu cầu refund
3. Seller kiểm tra review + ảnh + lý do
4. Nếu hợp lý → Seller gọi API refund:
   ```
   POST /api/seller/orders/{orderId}/refund
   ```
5. Hệ thống tự động:
   - Hoàn tiền về ví user
   - Trừ tiền từ ví seller
   - Cập nhật `paymentStatus: refunded`

---

## 6. API ENDPOINTS

### User APIs
```
POST   /api/user/orders                    # Tạo đơn hàng
POST   /api/user/orders/{id}/pay           # Thanh toán (wallet only)
GET    /api/user/orders                    # Lấy danh sách đơn hàng
GET    /api/user/orders/{id}               # Chi tiết đơn hàng
DELETE /api/user/orders/{id}               # Hủy đơn (chỉ khi pending)
```

### Seller APIs
```
GET    /api/seller/orders                  # Danh sách đơn hàng của shop
GET    /api/seller/orders/filter           # Lọc đơn theo status/date
POST   /api/seller/orders/{id}/confirm     # Xác nhận đơn hàng
POST   /api/seller/orders/{id}/deliver     # Đánh dấu đã giao
POST   /api/seller/orders/{id}/refund      # Hoàn tiền
POST   /api/seller/orders/{id}/cancel      # Hủy đơn (khi cần thiết)
GET    /api/seller/orders/statistics/*     # Thống kê doanh thu
```

---

## 7. QUY TẮC KINH DOANH

### 7.1. Hoa Hồng Platform
- **12%** trên mỗi đơn hàng
- Áp dụng cho cả ví app và COD
- Tính trên `totalAmount` (tổng tiền đơn hàng)

### 7.2. Phân Phối Tiền
```
Ví dụ: Đơn hàng 100,000 VND
- Buyer trả: 100,000 VND
- Platform nhận (12%): 12,000 VND
- Seller nhận (88%): 88,000 VND
```

### 7.3. Quyền Hủy Đơn
- **User**: Chỉ hủy được khi `orderStatus = pending` (chưa confirm)
- **Seller**: Có thể hủy `pending` hoặc `confirmed` (khi user yêu cầu qua chat)
- **Không ai**: Có thể hủy đơn đã `delivered`

### 7.4. Hoàn Tiền (Refund)
- Chỉ hoàn tiền cho đơn đã thanh toán
- Seller quyết định refund sau khi xem review
- Tiền hoàn về ví user, trừ từ ví seller
- Đơn COD không thể refund qua hệ thống

---

## 8. MIGRATION DATABASE

Chạy file migration để cập nhật schema:
```sql
-- File: migration_update_order_status.sql
-- Thêm cột confirmed_by, confirmed_at
-- Migrate dữ liệu cũ sang trạng thái mới
```

**Lưu ý**: Backup database trước khi chạy migration!

---

## 9. TESTING

### Test Cases
1. **Tạo đơn hàng** → Check status = pending
2. **Thanh toán wallet** → Check tiền đã trừ, status = paid
3. **Shop confirm** → Check status = confirmed
4. **Shop deliver** → Check status = delivered
5. **User hủy đơn pending** → Check hoàn tiền (nếu đã trả)
6. **Shop hủy đơn** → Check hoàn tiền (nếu đã trả)
7. **Shop refund** → Check tiền hoàn về user, trừ từ seller
8. **COD deliver** → Check payment status = paid

---

## 10. LƯU Ý QUAN TRỌNG

⚠️ **Đơn hàng COD**:
- Không thanh toán ngay khi đặt
- Thanh toán được xác nhận khi shop đánh dấu "delivered"
- Không thể refund qua hệ thống (seller tự xử lý ngoài)

⚠️ **Hoàn tiền**:
- Chỉ áp dụng cho đơn đã thanh toán qua ví app
- Seller có thể bị số dư âm nếu ví không đủ tiền refund

⚠️ **Xác nhận đơn hàng**:
- Sau khi thanh toán qua ví, đơn vẫn ở trạng thái `pending`
- Seller PHẢI xác nhận đơn thủ công
- User chỉ có thể hủy khi chưa confirm

---

**END OF DOCUMENT**

