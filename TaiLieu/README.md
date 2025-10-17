# 🍜 Food Tour Platform - Sàn Thương Mại Điện Tử Đồ Ăn

![Food Tour Platform](https://via.placeholder.com/800x300/4CAF50/FFFFFF?text=Food+Tour+Platform)

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/your-repo/food-tour-platform)
[![License](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
[![Node.js](https://img.shields.io/badge/node.js-18%2B-brightgreen.svg)](https://nodejs.org/)
[![MySQL](https://img.shields.io/badge/mysql-8.0%2B-orange.svg)](https://www.mysql.com/)

## 📖 Mục Lục

- [Tổng Quan](#-tổng-quan)
- [Tính Năng Chính](#-tính-năng-chính)
- [Kiến Trúc Hệ Thống](#-kiến-trúc-hệ-thống)
- [Cài Đặt](#-cài-đặt)
- [Cấu Hình](#-cấu-hình)
- [API Documentation](#-api-documentation)
- [Database Schema](#-database-schema)
- [Quyền & Roles](#-quyền--roles)
- [Screenshots](#-screenshots)
- [Roadmap](#-roadmap)
- [Contributing](#-contributing)
- [License](#-license)

## 🎯 Tổng Quan

**Food Tour Platform** là một sàn thương mại điện tử chuyên về đồ ăn, kết nối người mua với các cửa hàng địa phương thông qua một nền tảng toàn diện. Hệ thống hỗ trợ 4 loại người dùng chính với các tính năng phong phú từ mua bán, chat, đánh giá đến hệ thống affiliate marketing.

### 🌟 Điểm Nổi Bật

- **🗺️ Tìm kiếm trên bản đồ**: GPS tracking và map integration
- **💬 Real-time Chat**: WebSocket messaging system
- **🎫 Voucher System**: Flexible discount management
- **📱 Mobile-first**: Responsive design cho mọi thiết bị
- **🔐 Multi-role**: 4 loại người dùng với quyền khác nhau
- **📊 Analytics**: Dashboard báo cáo chi tiết
- **🔔 Real-time Notifications**: Push notifications
- **⭐ Social Features**: Posts, reviews, follow system

## 🚀 Tính Năng Chính

### 👥 Hệ Thống Người Dùng (4 Roles)

#### 🛡️ **ADMIN** - Quản Trị Viên
- Toàn quyền quản lý hệ thống
- Xem tất cả tin nhắn và hoạt động
- Quản lý users, shops, products
- Duyệt nội dung và xử lý tranh chấp
- Analytics và báo cáo tổng hợp
- Cấu hình hệ thống

#### 👤 **USER** - Khách Hàng
- Tìm kiếm và đặt hàng trên map
- Quản lý giỏ hàng và đơn hàng
- Sử dụng mã giảm giá
- Chat với seller
- Đánh giá và review
- Social features (posts, follow)

#### 🏪 **SELLER** - Người Bán
- Quản lý cửa hàng và sản phẩm
- Xử lý đơn hàng realtime
- Tạo voucher và khuyến mãi
- Chat với khách hàng
- Analytics doanh số
- Quản lý inventory

#### 💰 **RESELLER** - Đại Lý
- Hệ thống affiliate marketing
- Tracking hoa hồng
- Referral system
- Marketing tools
- Commission withdrawal

### 🛍️ Tính Năng Mua Bán

#### 🛒 **Shopping Experience**
```
🔍 Tìm kiếm thông minh
🗺️ Map-based discovery
🛍️ Advanced filtering
⭐ Rating & Reviews
💝 Wishlist & Favorites
🔔 Price alerts
```

#### 📦 **Order Management**
```
📋 Order tracking realtime
💳 Multiple payment methods
🚚 Delivery scheduling
📍 GPS delivery tracking
💬 Order chat support
🔄 Easy reordering
```

#### 🎫 **Voucher & Promotions**
```
🎯 Targeted discounts
⏰ Time-based promotions
🏪 Shop-specific vouchers
📱 Category discounts
👥 Bulk order discounts
🎁 Loyalty rewards
```

### 💬 Hệ Thống Chat & Social

#### 📱 **Messaging System**
- **Private Chat**: User ↔ Seller
- **Group Chat**: Multi-user conversations  
- **Support Chat**: Admin support
- **File Sharing**: Images, documents
- **Message Types**: Text, location, order info
- **Read Receipts**: Message status tracking

#### 🌐 **Social Features**
- **Posts & Feed**: User-generated content
- **Comments & Likes**: Social interactions
- **Follow System**: Follow users/shops
- **Reviews**: Rate products & shops
- **Photo Sharing**: Food photos & reviews

### 🗺️ Location & Map Features

#### 📍 **GPS Integration**
```javascript
// Map-based shop discovery
{
  "shops_near_me": {
    "radius": "5km",
    "coordinates": [21.0285, 105.8542],
    "filters": ["rating", "price", "cuisine_type"]
  }
}
```

#### 🌍 **Address System**
- **Multi-level**: Tỉnh/Thành → Quận/Huyện → Phường/Xã
- **GPS Coordinates**: Lat/Long for precise location
- **Delivery Zones**: Shop coverage areas
- **Address Validation**: Real address checking

## 🏗️ Kiến Trúc Hệ Thống

### 📊 **Technology Stack**

```
Frontend:
├── React.js / Next.js     # Web Application
├── React Native          # Mobile Apps
├── TypeScript           # Type Safety
├── Tailwind CSS        # Styling
├── Socket.IO Client    # Real-time Communication
└── Google Maps API     # Map Integration

Backend:
├── Node.js + Express   # API Server
├── Socket.IO           # WebSocket Server
├── JWT Authentication  # Security
├── Multer             # File Upload
├── Node-cron         # Scheduled Tasks
└── Bull Queue        # Background Jobs

Database:
├── MySQL 8.0+         # Primary Database
├── Redis             # Caching & Sessions
└── Elasticsearch     # Search Engine

Infrastructure:
├── Docker            # Containerization
├── AWS/GCP          # Cloud Platform
├── CloudFlare       # CDN
└── PM2              # Process Management
```

### 🔧 **System Architecture**

```
                    ┌─────────────────┐
                    │   Load Balancer │
                    │    (Nginx)      │
                    └─────────┬───────┘
                              │
                    ┌─────────▼───────┐
                    │   API Gateway   │
                    │  (Rate Limiting)│
                    └─────────┬───────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
    ┌─────▼─────┐      ┌─────▼─────┐      ┌─────▼─────┐
    │  Web App  │      │    API    │      │ WebSocket │
    │ (React)   │      │  Server   │      │  Server   │
    │           │      │(Express)  │      │(Socket.IO)│
    └───────────┘      └─────┬─────┘      └───────────┘
                              │
                    ┌─────────▼───────┐
                    │     MySQL       │
                    │   (Primary DB)  │
                    └─────────────────┘
```

## 💻 Cài Đặt

### 📋 **Yêu Cầu Hệ Thống**

```bash
# Software Requirements
Node.js >= 18.0.0
MySQL >= 8.0.0
Redis >= 6.0.0
Git >= 2.30.0

# Hardware Requirements (Production)
CPU: 4+ cores
RAM: 8GB+
Storage: 100GB+ SSD
Bandwidth: 1Gbps+
```

### 🛠️ **Cài Đặt Development**

#### 1️⃣ **Clone Repository**
```bash
git clone https://github.com/your-username/food-tour-platform.git
cd food-tour-platform
```

#### 2️⃣ **Backend Setup**
```bash
# Navigate to backend
cd backend

# Install dependencies
npm install

# Copy environment file
cp .env.example .env

# Create database
mysql -u root -p
CREATE DATABASE food_tour_platform;
EXIT;

# Run migrations
npm run migrate

# Seed initial data
npm run seed

# Start development server
npm run dev
```

#### 3️⃣ **Frontend Setup**
```bash
# Navigate to frontend
cd ../frontend

# Install dependencies
npm install

# Copy environment file
cp .env.example .env.local

# Start development server
npm run dev
```

#### 4️⃣ **Redis Setup**
```bash
# Install Redis (Ubuntu/Debian)
sudo apt update
sudo apt install redis-server

# Start Redis service
sudo systemctl start redis-server
sudo systemctl enable redis-server

# Verify Redis is running
redis-cli ping
# Expected output: PONG
```

### 🚀 **Production Deployment**

#### 🐳 **Docker Deployment**
```bash
# Build and run with Docker Compose
docker-compose up --build -d

# Scale services
docker-compose up --scale api=3 --scale websocket=2 -d

# View logs
docker-compose logs -f
```

#### ☁️ **Cloud Deployment (AWS)**
```bash
# Using AWS ECS/EKS
kubectl apply -f k8s/
```

## ⚙️ Cấu Hình

### 🔑 **Environment Variables**

#### **Backend (.env)**
```env
# Database
DB_HOST=localhost
DB_PORT=3306
DB_NAME=food_tour_platform
DB_USER=root
DB_PASSWORD=your_password

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET=your-super-secret-jwt-key
JWT_EXPIRES_IN=7d
REFRESH_TOKEN_EXPIRES_IN=30d

# File Upload
UPLOAD_PATH=/uploads
MAX_FILE_SIZE=10MB
ALLOWED_IMAGE_TYPES=jpg,jpeg,png,gif,webp

# Email (SMTP)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USER=your-email@gmail.com
SMTP_PASSWORD=your-app-password

# SMS (Optional)
SMS_API_KEY=your-sms-api-key
SMS_PROVIDER=twilio

# Payment Gateways
VNPAY_TMN_CODE=your-vnpay-code
VNPAY_SECRET_KEY=your-vnpay-secret
MOMO_PARTNER_CODE=your-momo-code
MOMO_ACCESS_KEY=your-momo-access-key
MOMO_SECRET_KEY=your-momo-secret

# Google Maps
GOOGLE_MAPS_API_KEY=your-google-maps-api-key

# CloudFlare (CDN)
CLOUDFLARE_ZONE_ID=your-zone-id
CLOUDFLARE_API_TOKEN=your-api-token

# Monitoring
SENTRY_DSN=your-sentry-dsn
NEW_RELIC_LICENSE_KEY=your-newrelic-key
```

#### **Frontend (.env.local)**
```env
# API Endpoints
NEXT_PUBLIC_API_URL=http://localhost:3001/api
NEXT_PUBLIC_WEBSOCKET_URL=ws://localhost:3001

# Google Maps
NEXT_PUBLIC_GOOGLE_MAPS_API_KEY=your-google-maps-api-key

# Firebase (Push Notifications)
NEXT_PUBLIC_FIREBASE_API_KEY=your-firebase-api-key
NEXT_PUBLIC_FIREBASE_PROJECT_ID=your-project-id

# Analytics
NEXT_PUBLIC_GA_TRACKING_ID=GA-XXXXXXXXX
NEXT_PUBLIC_FACEBOOK_PIXEL_ID=your-pixel-id
```

### 🔧 **Application Configuration**

#### **Rate Limiting**
```javascript
// config/rateLimit.js
module.exports = {
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // limit each IP to 100 requests per windowMs
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => req.ip,
  handler: (req, res) => {
    res.status(429).json({
      error: 'Too many requests, please try again later.'
    });
  }
};
```

#### **File Upload Configuration**
```javascript
// config/multer.js
const multer = require('multer');
const path = require('path');

const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, process.env.UPLOAD_PATH || './uploads/');
  },
  filename: (req, file, cb) => {
    const uniqueSuffix = Date.now() + '-' + Math.round(Math.random() * 1E9);
    cb(null, file.fieldname + '-' + uniqueSuffix + path.extname(file.originalname));
  }
});

module.exports = multer({ 
  storage,
  fileFilter: (req, file, cb) => {
    const allowedTypes = /jpeg|jpg|png|gif|webp/;
    const mimeType = allowedTypes.test(file.mimetype);
    if (mimeType) {
      return cb(null, true);
    } else {
      cb(new Error('Only image files are allowed!'));
    }
  },
  limits: {
    fileSize: 10 * 1024 * 1024 // 10MB
  }
});
```

## 📚 API Documentation

### 🔗 **Base URLs**
```
Development: http://localhost:3001/api
Production:  https://api.foodtour.com/api
WebSocket:   ws://localhost:3001
```

### 🔐 **Authentication**
```javascript
// Login Request
POST /api/auth/login
{
  "email": "user@example.com",
  "password": "password123"
}

// Response
{
  "success": true,
  "data": {
    "user": {
      "id": 1,
      "email": "user@example.com",
      "role": "user"
    },
    "tokens": {
      "accessToken": "eyJhbGciOiJIUzI1NiIs...",
      "refreshToken": "eyJhbGciOiJIUzI1NiIs..."
    }
  }
}

// Using token in requests
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

### 📖 **API Examples**

#### **🛍️ Browse Products**
```javascript
// Get products with filters
GET /api/public/products?category=1&location=hanoi&price_max=100000&rating_min=4

// Response
{
  "success": true,
  "data": {
    "products": [...],
    "pagination": {
      "page": 1,
      "limit": 20,
      "total": 150,
      "totalPages": 8
    },
    "filters": {
      "categories": [...],
      "priceRange": {...},
      "locations": [...]
    }
  }
}
```

#### **🛒 Add to Cart**
```javascript
// Add product to cart
POST /api/user/cart
{
  "product_id": 123,
  "quantity": 2,
  "selected_variants": [
    {
      "variant_name": "Size",
      "variant_value": "Large",
      "price_adjustment": 5.00
    }
  ],
  "special_instructions": "Extra spicy please"
}
```

#### **📦 Create Order**
```javascript
// Place order
POST /api/user/orders
{
  "shop_id": 45,
  "delivery_address_id": 12,
  "items": [
    {
      "product_id": 123,
      "quantity": 2,
      "selected_variants": [...]
    }
  ],
  "voucher_code": "DISCOUNT20",
  "payment_method": "card",
  "notes": "Please call when arriving"
}
```

#### **💬 Send Message**
```javascript
// Send message in conversation
POST /api/user/conversations/456/messages
{
  "message_type": "text",
  "content": "Hello, is the food ready?",
  "attachments": []
}

// WebSocket real-time message
socket.emit('send_message', {
  conversation_id: 456,
  message_type: 'text',
  content: 'Hello, is the food ready?'
});
```

### 📝 **API Response Format**
```javascript
// Success Response
{
  "success": true,
  "data": {...},
  "message": "Operation completed successfully",
  "timestamp": "2024-01-15T10:30:00Z"
}

// Error Response
{
  "success": false,
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input data",
    "details": {
      "email": ["Email is required"],
      "password": ["Password must be at least 8 characters"]
    }
  },
  "timestamp": "2024-01-15T10:30:00Z"
}
```

## 🗄️ Database Schema

### 📋 **Core Tables Overview**

```sql
-- Users and Authentication
users (id, username, email, role_id, ...)
roles (id, role_name, description)
addresses (id, user_id, latitude, longitude, ...)

-- Shop and Product Management
shops (id, seller_id, shop_name, address_id, ...)
categories (id, name, parent_id, ...)
products (id, shop_id, category_id, name, price, ...)
product_variants (id, product_id, variant_name, variant_value, ...)

-- Orders and Commerce
orders (id, user_id, shop_id, total_amount, status, ...)
order_items (id, order_id, product_id, quantity, ...)
cart_items (id, user_id, product_id, quantity, ...)
vouchers (id, code, discount_type, discount_value, ...)

-- Communication and Social
conversations (id, type, name, created_by, ...)
messages (id, conversation_id, sender_id, content, ...)
posts (id, user_id, title, content, ...)
reviews (id, user_id, reviewable_type, rating, ...)

-- System and Analytics
notifications (id, user_id, type, title, content, ...)
activity_logs (id, user_id, action, table_name, ...)
reseller_commissions (id, reseller_id, order_id, ...)
```

### 🔄 **Database Relationships**

```
Users (1:M) Shops
Users (1:M) Orders
Users (1:M) Messages
Users (1:M) Reviews

Shops (1:M) Products
Products (1:M) OrderItems
Products (1:M) ProductVariants

Orders (1:M) OrderItems
Orders (1:1) Vouchers (optional)

Conversations (1:M) Messages
Conversations (M:M) Users (participants)
```

### 📊 **Database Indexes**
```sql
-- Performance optimization indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_addresses_location ON addresses(latitude, longitude);
CREATE INDEX idx_products_shop_category ON products(shop_id, category_id);
CREATE INDEX idx_orders_user_status ON orders(user_id, order_status);
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at);
```

## 👥 Quyền & Roles

### 🛡️ **Permission Matrix**

| Feature | Admin | User | Seller | Reseller |
|---------|-------|------|--------|----------|
| View all users | ✅ | ❌ | ❌ | ❌ |
| Manage shops | ✅ | ❌ | Own only | ❌ |
| View all orders | ✅ | Own only | Shop only | Referred only |
| Access all messages | ✅ | Own only | Shop related | Own only |
| Create system vouchers | ✅ | ❌ | Shop only | ❌ |
| Approve content | ✅ | ❌ | ❌ | ❌ |
| View analytics | ✅ | Basic | Shop only | Commission only |
| Manage commissions | ✅ | ❌ | ❌ | Own only |

### 🔐 **Role-based Access Control (RBAC)**

```javascript
// Middleware for role checking
const checkRole = (allowedRoles) => {
  return (req, res, next) => {
    const userRole = req.user.role;
    
    if (!allowedRoles.includes(userRole)) {
      return res.status(403).json({
        success: false,
        error: 'Access denied. Insufficient permissions.'
      });
    }
    
    next();
  };
};

// Usage in routes
app.get('/api/admin/users', 
  authenticateToken,
  checkRole(['admin']),
  getUsersController
);

app.get('/api/seller/orders',
  authenticateToken,
  checkRole(['admin', 'seller']),
  getSellerOrdersController
);
```

### 🚫 **Permission Boundaries**

#### **Admin Permissions**
- ✅ Full system access
- ✅ View/edit all data
- ✅ Delete users/content
- ✅ System configuration
- ✅ Monitor all activities

#### **User Permissions**
- ✅ Browse and purchase
- ✅ Manage own profile/orders
- ✅ Chat with sellers
- ✅ Write reviews/posts
- ❌ Access other users' data
- ❌ Shop management features

#### **Seller Permissions**
- ✅ Manage own shop/products
- ✅ Process shop orders
- ✅ Chat with customers
- ✅ Create shop vouchers
- ❌ Access other shops' data
- ❌ System administration

#### **Reseller Permissions**
- ✅ View referral stats
- ✅ Manage marketing links
- ✅ Track commissions
- ✅ Withdraw earnings
- ❌ Access user/shop data
- ❌ Process orders directly

## 📱 Screenshots

### 🏠 **Homepage & Discovery**
```
[🖼️ Homepage with featured shops and map view]
[🖼️ Product search and filtering interface]
[🖼️ Map-based shop discovery]
```

### 🛍️ **Shopping Experience**
```
[🖼️ Product detail page with variants]
[🖼️ Shopping cart and checkout flow]
[🖼️ Order tracking interface]
```

### 💬 **Chat & Social**
```
[🖼️ Real-time chat interface]
[🖼️ Social feed with posts and reviews]
[🖼️ User profile and following system]
```

### 📊 **Admin Dashboard**
```
[🖼️ Admin analytics dashboard]
[🖼️ User management interface]
[🖼️ Order monitoring system]
```

### 🏪 **Seller Panel**
```
[🖼️ Shop management dashboard]
[🖼️ Product inventory interface]
[🖼️ Order processing workflow]
```

## 🛣️ Roadmap

### 📅 **Version 1.0.0** (Current)
- ✅ Core marketplace functionality
- ✅ Multi-role user system
- ✅ Real-time chat
- ✅ Map-based discovery
- ✅ Basic payment integration

### 📅 **Version 1.1.0** (Q2 2024)
- 🔄 AI-powered recommendations
- 🔄 Advanced analytics dashboard
- 🔄 Mobile app (React Native)
- 🔄 Voice ordering
- 🔄 Loyalty program

### 📅 **Version 1.2.0** (Q3 2024)
- 🔄 Delivery tracking GPS
- 🔄 Video calls for support
- 🔄 AR menu viewing
- 🔄 Subscription boxes
- 🔄 Multi-language support

### 📅 **Version 2.0.0** (Q4 2024)
- 🔄 Blockchain integration
- 🔄 NFT loyalty tokens
- 🔄 Metaverse shop tours
- 🔄 AI chatbot support
- 🔄 Advanced ML recommendations

## 🤝 Contributing

### 📋 **Development Guidelines**

#### **Code Standards**
```bash
# Linting
npm run lint

# Testing  
npm run test
npm run test:coverage

# Type checking
npm run type-check

# Build
npm run build
```

#### **Git Workflow**
```bash
# Feature branch
git checkout -b feature/new-chat-system
git commit -m "feat: add real-time chat system"
git push origin feature/new-chat-system

# Pull request
gh pr create --title "Add real-time chat system" --body "..."

# After review and approval
git checkout main
git pull origin main
git branch -d feature/new-chat-system
```

#### **Commit Message Convention**
```
feat: add new feature
fix: bug fix
docs: documentation changes
style: formatting, missing semicolons, etc
refactor: code refactoring
test: adding tests
chore: maintenance tasks

Examples:
feat: add real-time order tracking
fix: resolve payment gateway timeout issue
docs: update API documentation for chat endpoints
```

### 🧪 **Testing**

#### **Test Structure**
```
tests/
├── unit/           # Unit tests
│   ├── models/
│   ├── services/
│   └── utils/
├── integration/    # Integration tests
│   ├── api/
│   └── database/
├── e2e/           # End-to-end tests
│   ├── user-flows/
│   └── admin-flows/
└── fixtures/      # Test data
```

#### **Running Tests**
```bash
# Unit tests
npm run test:unit

# Integration tests  
npm run test:integration

# E2E tests
npm run test:e2e

# Coverage report
npm run test:coverage
```

### 🐛 **Bug Reports**

**Before reporting:**
1. Check existing issues
2. Test on latest version
3. Provide reproduction steps
4. Include system information

**Bug report template:**
```markdown
## Bug Description
Brief description of the bug

## Steps to Reproduce
1. Step 1
2. Step 2
3. Step 3

## Expected Behavior
What should happen

## Actual Behavior  
What actually happens

## Environment
- OS: [e.g. Ubuntu 20.04]
- Node.js: [e.g. 18.17.0]
- Browser: [e.g. Chrome 91.0]

## Additional Context
Screenshots, logs, etc.
```

## 📄 License

### MIT License

```
MIT License

Copyright (c) 2024 Food Tour Platform

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 📞 Support & Contact

### 🆘 **Getting Help**

- **📖 Documentation**: [https://docs.foodtour.com](https://docs.foodtour.com)
- **💬 Community**: [https://discord.gg/foodtour](https://discord.gg/foodtour)
- **🐛 Issues**: [GitHub Issues](https://github.com/your-repo/issues)
- **📧 Email**: support@foodtour.com

### 👥 **Team**

- **Project Lead**: [Your Name](mailto:lead@foodtour.com)
- **Backend Developer**: [Developer Name](mailto:backend@foodtour.com)  
- **Frontend Developer**: [Developer Name](mailto:frontend@foodtour.com)
- **DevOps Engineer**: [DevOps Name](mailto:devops@foodtour.com)

### 🌟 **Acknowledgments**

- Thanks to all contributors
- Inspired by leading food delivery platforms
- Built with ❤️ for the food community

---

<div align="center">

**⭐ Star us on GitHub if this project helped you! ⭐**

[⬆ Back to Top](#-food-tour-platform---sàn-thương-mại-điện-tử-đồ-ăn)

Made with ❤️ by Food Tour Team

</div>
