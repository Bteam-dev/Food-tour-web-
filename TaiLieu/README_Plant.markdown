# 🍜 Food Tour Platform - Hướng Dẫn Phát Triển

Chào mừng bạn đến với **Food Tour Platform**, một nền tảng thương mại điện tử đồ ăn kết nối người dùng với các cửa hàng địa phương. README này cung cấp thứ tự thực hiện toàn bộ API cho cả ứng dụng di động (Android, Jetpack Compose, Kotlin) và web (React.js/Next.js, TypeScript, Tailwind CSS), cùng với công nghệ sử dụng cho từng nhóm API, hướng dẫn cài đặt và cấu hình.

---

## 📖 Mục Lục
- [Tổng Quan](#-tổng-quan)
- [Thứ Tự Thực Hiện API](#-thứ-tự-thực-hiện-api)
- [Công Nghệ Sử Dụng Từng API](#-công-nghệ-sử-dụng-từng-api)
- [Cài Đặt](#-cài-đặt)
- [Cấu Hình](#-cấu-hình)
- [Hỗ Trợ & Liên Hệ](#-hỗ-trợ--liên-hệ)

---

## 🎯 Tổng Quan
**Food Tour Platform** là một nền tảng thương mại điện tử đồ ăn hỗ trợ 4 vai trò: **Admin**, **User**, **Seller**, và **Reseller**. Nền tảng cung cấp các tính năng như tìm kiếm cửa hàng trên bản đồ, chat thời gian thực, quản lý đơn hàng, hệ thống voucher, và tích hợp thanh toán. Các API được thiết kế để hoạt động đồng bộ trên cả ứng dụng di động và web.

**Điểm Nổi Bật**:
- 🗺️ Tìm kiếm cửa hàng qua bản đồ với GPS.
- 💬 Chat thời gian thực qua WebSocket.
- 🎫 Hệ thống voucher và khuyến mãi linh hoạt.
- 📱 Giao diện tối ưu cho mobile (Jetpack Compose) và web (Tailwind CSS).
- 🔐 Quản lý quyền theo vai trò (RBAC).
- 🔔 Thông báo đẩy (mobile) và thời gian thực (web).
- 📊 Phân tích và báo cáo chi tiết.

---

## 🔄 Thứ Tự Thực Hiện API
Dưới đây là thứ tự thực hiện toàn bộ API theo luồng logic, áp dụng cho cả mobile và web. Các API được nhóm theo chức năng và sắp xếp theo quy trình người dùng thực tế.

### 1. Khởi Tạo Hệ Thống
1. `GET /api/health` - Kiểm tra sức khỏe hệ thống.
2. `GET /api/status` - Kiểm tra trạng thái hệ thống.
3. `GET /api/version` - Kiểm tra phiên bản API.
4. `GET /api/mobile/app/version` - Kiểm tra phiên bản ứng dụng (mobile).
5. `GET /api/mobile/config` - Tải cấu hình ứng dụng (mobile).
6. `POST /api/mobile/device/register` - Đăng ký token thiết bị cho thông báo đẩy (mobile).

### 2. Xác Thực & Đăng Ký
1. `POST /api/auth/register` - Đăng ký tài khoản.
2. `POST /api/auth/verify-email` - Xác thực email.
3. `POST /api/auth/login` - Đăng nhập.
4. `POST /api/auth/forgot-password` - Yêu cầu đặt lại mật khẩu.
5. `POST /api/auth/reset-password` - Đặt lại mật khẩu.
6. `POST /api/auth/refresh-token` - Làm mới token.

### 3. Duyệt & Tìm Kiếm (Khách)
1. `GET /api/public/system/settings` - Tải cấu hình công khai (phí vận chuyển, v.v.).
2. `GET /api/public/locations/provinces` - Danh sách tỉnh/thành.
3. `GET /api/public/locations/districts/{province_id}` - Danh sách quận/huyện.
4. `GET /api/public/locations/wards/{district_id}` - Danh sách phường/xã.
5. `POST /api/public/geocoding` - Chuyển địa chỉ thành tọa độ.
6. `GET /api/public/shops` - Danh sách cửa hàng (có thể lọc theo vị trí).
7. `GET /api/public/shops/{id}` - Chi tiết cửa hàng.
8. `GET /api/public/products` - Danh sách sản phẩm.
9. `GET /api/public/products/{id}` - Chi tiết sản phẩm.
10. `GET /api/public/categories` - Danh sách danh mục.
11. `GET /api/public/search` - Tìm kiếm shop/sản phẩm.
12. `GET /api/public/map/shops` - Cửa hàng trên bản đồ.
13. `GET /api/public/reviews/{type}/{id}` - Đánh giá shop/sản phẩm.
14. `GET /api/public/posts` - Bài đăng công khai.
15. `GET /api/public/posts/{id}` - Chi tiết bài đăng.

### 4. Quản Lý Hồ Sơ Người Dùng
1. `GET /api/user/profile` - Xem thông tin cá nhân.
2. `PUT /api/user/profile` - Cập nhật thông tin.
3. `POST /api/user/avatar` - Upload avatar.
4. `POST /api/user/change-password` - Đổi mật khẩu.

### 5. Quản Lý Địa Chỉ
1. `GET /api/user/addresses` - Danh sách địa chỉ.
2. `POST /api/user/addresses` - Thêm địa chỉ.
3. `PUT /api/user/addresses/{id}` - Sửa địa chỉ.
4. `DELETE /api/user/addresses/{id}` - Xóa địa chỉ.
5. `PUT /api/user/addresses/{id}/set-default` - Đặt địa chỉ mặc định.

### 6. Mua Sắm & Giỏ Hàng
1. `GET /api/user/cart` - Xem giỏ hàng.
2. `POST /api/user/cart` - Thêm sản phẩm vào giỏ.
3. `PUT /api/user/cart/{id}` - Cập nhật số lượng.
4. `DELETE /api/user/cart/{id}` - Xóa sản phẩm khỏi giỏ.
5. `DELETE /api/user/cart/clear` - Xóa toàn bộ giỏ.

### 7. Đặt Hàng
1. `GET /api/user/vouchers/available` - Xem voucher khả dụng.
2. `GET /api/user/vouchers/my-vouchers` - Xem voucher của tôi.
3. `POST /api/user/vouchers/apply` - Áp dụng voucher.
4. `POST /api/user/vouchers/collect/{code}` - Thu thập voucher.
5. `POST /api/user/orders` - Đặt hàng.
6. `GET /api/user/orders` - Lịch sử đơn hàng.
7. `GET /api/user/orders/{id}` - Chi tiết đơn hàng.
8. `PUT /api/user/orders/{id}/cancel` - Hủy đơn.
9. `POST /api/user/orders/{id}/reorder` - Đặt lại đơn.
10. `GET /api/user/orders/{id}/tracking` - Theo dõi đơn.
11. `WS /ws/orders/{order_id}` - Cập nhật đơn hàng thời gian thực.

### 8. Đánh Giá & Bình Luận
1. `POST /api/user/reviews` - Viết đánh giá.
2. `GET /api/user/reviews` - Đánh giá của tôi.
3. `PUT /api/user/reviews/{id}` - Sửa đánh giá.
4. `DELETE /api/user/reviews/{id}` - Xóa đánh giá.
5. `POST /api/user/reviews/{id}/helpful` - Đánh giá hữu ích.

### 9. Tính Năng Xã Hội
1. `GET /api/user/favorites` - Danh sách yêu thích.
2. `POST /api/user/favorites` - Thêm yêu thích.
3. `DELETE /api/user/favorites/{type}/{id}` - Bỏ yêu thích.
4. `GET /api/user/following` - Đang theo dõi.
5. `POST /api/user/follow` - Theo dõi shop/user.
6. `DELETE /api/user/follow/{type}/{id}` - Bỏ theo dõi.
7. `GET /api/user/posts` - Bài đăng của tôi.
8. `POST /api/user/posts` - Tạo bài đăng.
9. `PUT /api/user/posts/{id}` - Sửa bài đăng.
10. `DELETE /api/user/posts/{id}` - Xóa bài đăng.
11. `POST /api/user/posts/{id}/like` - Thích bài đăng.
12. `DELETE /api/user/posts/{id}/like` - Bỏ thích.
13. `POST /api/user/posts/{id}/comments` - Bình luận.
14. `PUT /api/user/comments/{id}` - Sửa bình luận.
15. `DELETE /api/user/comments/{id}` - Xóa bình luận.

### 10. Chat
1. `GET /api/user/conversations` - Danh sách cuộc trò chuyện.
2. `POST /api/user/conversations` - Tạo cuộc trò chuyện.
3. `GET /api/user/conversations/{id}/messages` - Tin nhắn.
4. `POST /api/user/conversations/{id}/messages` - Gửi tin nhắn.
5. `PUT /api/user/messages/{id}` - Sửa tin nhắn.
6. `DELETE /api/user/messages/{id}` - Xóa tin nhắn.
7. `PUT /api/user/conversations/{id}/read` - Đánh dấu đã đọc.
8. `WS /ws/chat/{conversation_id}` - Chat thời gian thực.

### 11. Thông Báo
1. `GET /api/user/notifications` - Danh sách thông báo.
2. `PUT /api/user/notifications/{id}/read` - Đánh dấu đã đọc.
3. `PUT /api/user/notifications/read-all` - Đánh dấu tất cả đã đọc.
4. `DELETE /api/user/notifications/{id}` - Xóa thông báo.
5. `WS /ws/notifications/{user_id}` - Thông báo thời gian thực.
6. `POST /api/mobile/push/test` - Kiểm tra thông báo đẩy (mobile).

### 12. Quản Lý Cửa Hàng (Seller)
1. `GET /api/seller/shop` - Xem thông tin cửa hàng.
2. `POST /api/seller/shop` - Tạo cửa hàng.
3. `PUT /api/seller/shop` - Cập nhật cửa hàng.
4. `POST /api/seller/shop/logo` - Upload logo.
5. `POST /api/seller/shop/banner` - Upload banner.
6. `PUT /api/seller/shop/hours` - Cập nhật giờ mở cửa.
7. `PUT /api/seller/shop/status` - Bật/tắt cửa hàng.
8. `GET /api/seller/products` - Danh sách sản phẩm.
9. `POST /api/seller/products` - Thêm sản phẩm.
10. `GET /api/seller/products/{id}` - Chi tiết sản phẩm.
11. `PUT /api/seller/products/{id}` - Cập nhật sản phẩm.
12. `DELETE /api/seller/products/{id}` - Xóa sản phẩm.
13. `PUT /api/seller/products/{id}/status` - Bật/tắt sản phẩm.
14. `POST /api/seller/products/{id}/images` - Upload ảnh sản phẩm.
15. `DELETE /api/seller/products/{id}/images/{image_id}` - Xóa ảnh.
16. `GET /api/seller/products/{id}/variants` - Biến thể sản phẩm.
17. `POST /api/seller/products/{id}/variants` - Thêm biến thể.
18. `PUT /api/seller/products/variants/{id}` - Cập nhật biến thể.
19. `DELETE /api/seller/products/variants/{id}` - Xóa biến thể.
20. `GET /api/seller/orders` - Danh sách đơn hàng.
21. `GET /api/seller/orders/{id}` - Chi tiết đơn hàng.
22. `PUT /api/seller/orders/{id}/confirm` - Xác nhận đơn.
23. `PUT /api/seller/orders/{id}/preparing` - Đang chuẩn bị.
24. `PUT /api/seller/orders/{id}/ready` - Sẵn sàng giao.
25. `PUT /api/seller/orders/{id}/delivering` - Đang giao.
26. `PUT /api/seller/orders/{id}/delivered` - Đã giao.
27. `PUT /api/seller/orders/{id}/cancel` - Hủy đơn.
28. `GET /api/seller/vouchers` - Voucher của shop.
29. `POST /api/seller/vouchers` - Tạo voucher.
30. `PUT /api/seller/vouchers/{id}` - Cập nhật voucher.
31. `DELETE /api/seller/vouchers/{id}` - Xóa voucher.
32. `PUT /api/seller/vouchers/{id}/status` - Bật/tắt voucher.
33. `GET /api/seller/vouchers/{id}/usage` - Thống kê sử dụng voucher.
34. `GET /api/seller/reviews` - Đánh giá của shop.
35. `POST /api/seller/reviews/{id}/reply` - Phản hồi đánh giá.
36. `PUT /api/seller/reviews/{id}/reply` - Sửa phản hồi.
37. `GET /api/seller/analytics/overview` - Tổng quan doanh số.
38. `GET /api/seller/analytics/orders` - Thống kê đơn hàng.
39. `GET /api/seller/analytics/products` - Thống kê sản phẩm.
40. `GET /api/seller/analytics/revenue` - Doanh thu theo thời gian.
41. `GET /api/seller/analytics/customers` - Khách hàng thường xuyên.
42. `GET /api/seller/conversations` - Cuộc trò chuyện với khách.
43. `POST /api/seller/conversations/{id}/messages` - Phản hồi khách.
44. `GET /api/seller/support/conversations` - Hỗ trợ từ admin.

### 13. Hệ Thống Đại Lý (Reseller)
1. `GET /api/reseller/profile` - Xem thông tin reseller.
2. `PUT /api/reseller/profile` - Cập nhật thông tin.
3. `GET /api/reseller/stats` - Thống kê tổng quan.
4. `GET /api/reseller/commissions` - Danh sách hoa hồng.
5. `GET /api/reseller/commissions/pending` - Hoa hồng chờ duyệt.
6. `GET /api/reseller/commissions/paid` - Hoa hồng đã thanh toán.
7. `POST /api/reseller/commissions/withdraw` - Rút hoa hồng.
8. `GET /api/reseller/referral-code` - Mã giới thiệu.
9. `POST /api/reseller/referral-code/generate` - Tạo mã mới.
10. `GET /api/reseller/referrals` - Danh sách người được giới thiệu.
11. `GET /api/reseller/referrals/stats` - Thống kê giới thiệu.
12. `GET /api/reseller/marketing/banners` - Banner marketing.
13. `GET /api/reseller/marketing/links` - Link affiliate.
14. `POST /api/reseller/marketing/campaigns` - Tạo chiến dịch.
15. `GET /api/reseller/marketing/performance` - Hiệu quả chiến dịch.
16. `GET /api/reseller/orders` - Đơn hàng từ người giới thiệu.
17. `GET /api/reseller/orders/{id}` - Chi tiết đơn từ referral.

### 14. Quản Trị (Admin)
1. `GET /api/admin/users` - Danh sách tất cả user.
2. `GET /api/admin/users/{id}` - Chi tiết user.
3. `PUT /api/admin/users/{id}` - Cập nhật thông tin user.
4. `PUT /api/admin/users/{id}/status` - Khóa/mở khóa user.
5. `DELETE /api/admin/users/{id}` - Xóa user.
6. `PUT /api/admin/users/{id}/role` - Thay đổi vai trò.
7. `GET /api/admin/users/{id}/activity` - Lịch sử hoạt động.
8. `GET /api/admin/shops` - Danh sách tất cả shop.
9. `GET /api/admin/shops/pending` - Shop chờ duyệt.
10. `PUT /api/admin/shops/{id}/verify` - Duyệt shop.
11. `PUT /api/admin/shops/{id}/reject` - Từ chối shop.
12. `PUT /api/admin/shops/{id}/status` - Bật/tắt shop.
13. `DELETE /api/admin/shops/{id}` - Xóa shop.
14. `GET /api/admin/products` - Tất cả sản phẩm.
15. `GET /api/admin/products/pending` - Sản phẩm chờ duyệt.
16. `PUT /api/admin/products/{id}/approve` - Duyệt sản phẩm.
17. `PUT /api/admin/products/{id}/reject` - Từ chối sản phẩm.
18. `DELETE /api/admin/products/{id}` - Xóa sản phẩm.
19. `GET /api/admin/categories` - Danh sách danh mục.
20. `POST /api/admin/categories` - Thêm danh mục.
21. `PUT /api/admin/categories/{id}` - Sửa danh mục.
22. `DELETE /api/admin/categories/{id}` - Xóa danh mục.
23. `PUT /api/admin/categories/{id}/status` - Bật/tắt danh mục.
24. `GET /api/admin/orders` - Tất cả đơn hàng.
25. `GET /api/admin/orders/{id}` - Chi tiết đơn hàng.
26. `PUT /api/admin/orders/{id}/status` - Cập nhật trạng thái.
27. `GET /api/admin/orders/disputes` - Đơn hàng tranh chấp.
28. `POST /api/admin/orders/{id}/refund` - Hoàn tiền.
29. `GET /api/admin/vouchers` - Tất cả voucher.
30. `POST /api/admin/vouchers/system` - Tạo voucher hệ thống.
31. `PUT /api/admin/vouchers/{id}` - Cập nhật voucher.
32. `DELETE /api/admin/vouchers/{id}` - Xóa voucher.
33. `GET /api/admin/vouchers/{id}/usage` - Thống kê sử dụng.
34. `GET /api/admin/posts` - Tất cả bài đăng.
35. `PUT /api/admin/posts/{id}/approve` - Duyệt bài đăng.
36. `PUT /api/admin/posts/{id}/reject` - Từ chối bài đăng.
37. `DELETE /api/admin/posts/{id}` - Xóa bài đăng.
38. `PUT /api/admin/posts/{id}/feature` - Đánh dấu nổi bật.
39. `GET /api/admin/reviews` - Tất cả đánh giá.
40. `PUT /api/admin/reviews/{id}/approve` - Duyệt đánh giá.
41. `DELETE /api/admin/reviews/{id}` - Xóa đánh giá.
42. `GET /api/admin/reviews/flagged` - Đánh giá bị báo cáo.
43. `GET /api/admin/conversations` - Tất cả cuộc trò chuyện.
44. `GET /api/admin/conversations/{id}/messages` - Tin nhắn trong cuộc trò chuyện.
45. `POST /api/admin/conversations/{id}/join` - Tham gia cuộc trò chuyện.
46. `POST /api/admin/messages/{id}/flag` - Đánh dấu tin nhắn vi phạm.
47. `DELETE /api/admin/messages/{id}` - Xóa tin nhắn.
48. `GET /api/admin/commissions` - Tất cả hoa hồng.
49. `PUT /api/admin/commissions/{id}/approve` - Duyệt hoa hồng.
50. `PUT /api/admin/commissions/{id}/reject` - Từ chối hoa hồng.
51. `POST /api/admin/commissions/{id}/pay` - Thanh toán hoa hồng.
52. `GET /api/admin/commissions/stats` - Thống kê hoa hồng.
53. `GET /api/admin/settings` - Cấu hình hệ thống.
54. `PUT /api/admin/settings` - Cập nhật cấu hình.
55. `GET /api/admin/settings/payment` - Cài đặt thanh toán.
56. `PUT /api/admin/settings/payment` - Cập nhật thanh toán.
57. `GET /api/admin/settings/notification` - Cài đặt thông báo.
58. `PUT /api/admin/settings/notification` - Cập nhật thông báo.
59. `GET /api/admin/analytics/overview` - Tổng quan hệ thống.
60. `GET /api/admin/analytics/users` - Thống kê người dùng.
61. `GET /api/admin/analytics/shops` - Thống kê cửa hàng.
62. `GET /api/admin/analytics/orders` - Thống kê đơn hàng.
63. `GET /api/admin/analytics/revenue` - Thống kê doanh thu.
64. `GET /api/admin/analytics/products` - Sản phẩm bán chạy.
65. `POST /api/admin/reports/export` - Xuất báo cáo.
66. `GET /api/admin/logs/activity` - Log hoạt động hệ thống.
67. `GET /api/admin/logs/users/{id}` - Log hoạt động user.
68. `GET /api/admin/logs/orders` - Log đơn hàng.
69. `GET /api/admin/logs/payments` - Log thanh toán.
70. `POST /api/admin/notifications/broadcast` - Gửi thông báo tổng.
71. `POST /api/admin/notifications/targeted` - Gửi thông báo nhóm.
72. `GET /api/admin/notifications/sent` - Lịch sử gửi thông báo.
73. `WS /ws/admin/monitoring` - Giám sát admin thời gian thực.

### 15. API Chung
1. `POST /api/upload/images` - Upload ảnh.
2. `POST /api/upload/files` - Upload file.
3. `DELETE /api/upload/{filename}` - Xóa file.
4. `GET /api/search/global` - Tìm kiếm toàn hệ thống.
5. `GET /api/filter/options` - Tùy chọn filter.
6. `POST /api/search/history` - Lưu lịch sử tìm kiếm.

### 16. API Bổ Sung
1. `POST /api/payments/vnpay/callback` - VNPay callback.
2. `POST /api/payments/momo/callback` - MoMo callback.
3. `POST /api/maps/geocoding` - Geocoding địa chỉ.
4. `POST /api/sms/send` - Gửi SMS OTP.
5. `POST /api/email/send` - Gửi email.

---

## 🛠️ Công Nghệ Sử Dụng Từng API
Dưới đây là công nghệ sử dụng cho từng nhóm API, phân biệt giữa frontend (mobile và web), backend, và các dịch vụ bổ sung.

| **Nhóm API**                | **Frontend (Mobile)**                 | **Frontend (Web)**                    | **Backend**                          | **Database**                   | **Dịch Vụ Bổ Sung**            |
|-----------------------------|---------------------------------------|---------------------------------------|--------------------------------------|--------------------------------|--------------------------------|
| **Khởi Tạo Hệ Thống**       | Jetpack Compose, WorkManager, Firebase | React.js, Axios                      | Node.js, Express, Firebase Admin    | MySQL, Redis                  | Firebase Cloud Messaging       |
| **Xác Thực**                | Jetpack Compose, DataStore, Retrofit  | React.js, localStorage, Axios        | Node.js, Express, JWT, Nodemailer   | MySQL, Redis                  | SMTP (email)                  |
| **Duyệt & Tìm Kiếm**        | Jetpack Compose, Google Maps SDK, Coil | React.js, Google Maps API, Tailwind CSS | Node.js, Express, Elasticsearch   | MySQL, Redis                  | Google Maps API               |
| **Quản Lý Hồ Sơ**           | Jetpack Compose, ImagePicker, Retrofit | React.js, FileReader, Axios          | Node.js, Express, Multer            | MySQL, AWS S3                 | -                             |
| **Quản Lý Địa Chỉ**         | Jetpack Compose, Google Maps SDK      | React.js, Google Maps API, Tailwind CSS | Node.js, Express, Google Maps API | MySQL                         | -                             |
| **Mua Sắm & Giỏ Hàng**      | Jetpack Compose, Room, Retrofit       | React.js, Redux, Axios               | Node.js, Express, Bull Queue        | MySQL                         | -                             |
| **Đặt Hàng**                | Jetpack Compose, WorkManager, Retrofit | React.js, Axios                      | Node.js, Express, Node-cron         | MySQL                         | VNPay, MoMo                   |
| **Voucher**                 | Jetpack Compose, Retrofit             | React.js, Tailwind CSS, Axios        | Node.js, Express                    | MySQL, Redis                  | -                             |
| **Đánh Giá & Bình Luận**    | Jetpack Compose, Retrofit             | React.js, Axios                      | Node.js, Express                    | MySQL                         | -                             |
| **Tính Năng Xã Hội**        | Jetpack Compose, Coil, Retrofit       | React.js, Tailwind CSS, Axios        | Node.js, Express                    | MySQL                         | -                             |
| **Chat**                    | Jetpack Compose, OkHttp (WebSocket)   | React.js, Socket.IO Client           | Node.js, Socket.IO                  | MySQL, Redis                  | -                             |
| **Thông Báo**               | Jetpack Compose, Firebase FCM         | React.js, Axios                      | Node.js, Express, Firebase Admin    | MySQL                         | Firebase Cloud Messaging       |
| **Quản Lý Cửa Hàng**        | Jetpack Compose, ImagePicker, Retrofit | React.js, FileReader, Axios          | Node.js, Express, Multer            | MySQL, Elasticsearch          | AWS S3                        |
| **Hệ Thống Đại Lý**         | Jetpack Compose, QRCode-Kotlin        | React.js, QRCode, Axios              | Node.js, Express                    | MySQL                         | -                             |
| **Quản Trị**                | Jetpack Compose, Recharts, Retrofit   | React.js, Recharts, Axios            | Node.js, Express                    | MySQL, Elasticsearch          | -                             |
| **API Chung**               | Jetpack Compose, Retrofit             | React.js, Axios                      | Node.js, Express, Multer            | MySQL, Redis                  | AWS S3                        |
| **API Bổ Sung**             | Jetpack Compose, Retrofit             | React.js, Axios                      | Node.js, Express                    | MySQL                         | VNPay, MoMo, Twilio           |

---

## 💻 Cài Đặt
### 📋 Yêu Cầu Hệ Thống
- **Phần mềm**:
  - Android Studio >= 2023.1.1 (cho mobile)
  - Node.js >= 18.0.0
  - MySQL >= 8.0.0
  - Redis >= 6.0.0
- **Phần cứng**:
  - CPU: 4+ lõi
  - RAM: 8GB+
  - Dung lượng: 100GB+ SSD

### 🛠️ Thiết Lập Development
1. **Clone Repository**:
   ```bash
   git clone https://github.com/your-username/food-tour-platform.git
   cd food-tour-platform
   ```

2. **Backend Setup**:
   ```bash
   cd backend
   npm install
   cp .env.example .env
   mysql -u root -p
   CREATE DATABASE food_tour_platform;
   EXIT;
   npm run migrate
   npm run seed
   npm run dev
   ```

3. **Mobile App Setup** (Android):
   ```bash
   cd mobile
   # Mở Android Studio, import project
   # Cập nhật build.gradle
   implementation "androidx.compose.ui:ui:1.6.0"
   implementation "com.google.maps.android:maps-compose:2.11.0"
   implementation "com.squareup.retrofit2:retrofit:2.9.0"
   implementation "com.google.firebase:firebase-messaging:23.0.0"
   # Sync project và chạy trên emulator/thiết bị Android
   ```

4. **Web App Setup**:
   ```bash
   cd web
   npm install
   cp .env.example .env.local
   npm run dev
   ```

5. **Redis Setup**:
   ```bash
   sudo apt update
   sudo apt install redis-server
   sudo systemctl start redis-server
   redis-cli ping  # Kết quả: PONG
   ```

---

## ⚙️ Cấu Hình
### 🔑 Biến Môi Trường
#### Backend (.env)
```plaintext
DB_HOST=localhost
DB_PORT=3306
DB_NAME=food_tour_platform
DB_USER=root
DB_PASSWORD=your_password
REDIS_HOST=localhost
REDIS_PORT=6379
JWT_SECRET=your-super-secret-jwt-key
GOOGLE_MAPS_API_KEY=your-google-maps-api-key
VNPAY_TMN_CODE=your-vnpay-code
MOMO_PARTNER_CODE=your-momo-code
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASSWORD=your-app-password
```

#### Mobile App (local.properties)
```plaintext
API_URL=http://localhost:3001/api
WEBSOCKET_URL=ws://localhost:3001
GOOGLE_MAPS_API_KEY=your-google-maps-api-key
FIREBASE_API_KEY=your-firebase-api-key
```

#### Web App (.env.local)
```plaintext
NEXT_PUBLIC_API_URL=http://localhost:3001/api
NEXT_PUBLIC_WEBSOCKET_URL=ws://localhost:3001
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=your-google-maps-api-key
```

---

## 📞 Hỗ Trợ & Liên Hệ
- **Tài liệu**: [docs.foodtour.com](https://docs.foodtour.com)
- **Email**: [support@foodtour.com](mailto:support@foodtour.com)
- **Cộng đồng**: [Discord](https://discord.gg/foodtour)

---

**Được xây dựng với ❤️ bởi Food Tour Team**