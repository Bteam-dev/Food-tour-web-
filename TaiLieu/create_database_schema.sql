-- =============================================
-- FoodTourApp Database Schema
-- Created: 2026-02-23
-- Description: Complete database schema for FoodTourApp
-- =============================================

-- Drop existing database if exists (CAREFUL!)
-- DROP DATABASE IF EXISTS foodtourapp;

-- Create database
CREATE DATABASE IF NOT EXISTS foodtourapp
CHARACTER SET utf8mb4
COLLATE utf8mb4_unicode_ci;

USE foodtourapp;

-- =============================================
-- TABLE: roles
-- Description: User roles (customer, seller, admin)
-- =============================================
CREATE TABLE roles (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE COMMENT 'Role name: CUSTOMER, SELLER, ADMIN',
    description VARCHAR(255) COMMENT 'Role description',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: users
-- Description: User accounts
-- =============================================
CREATE TABLE users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT 'Login username',
    email VARCHAR(100) NOT NULL UNIQUE COMMENT 'User email',
    password VARCHAR(255) NOT NULL COMMENT 'Hashed password',
    full_name VARCHAR(100) NOT NULL COMMENT 'User full name',
    phone_number VARCHAR(20) COMMENT 'Phone number',
    avatar_url VARCHAR(500) COMMENT 'Avatar image URL',
    date_of_birth DATE COMMENT 'Date of birth',
    gender ENUM('male', 'female', 'other') COMMENT 'User gender',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Account active status',
    is_verified BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Email verified status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_username (username),
    INDEX idx_email (email),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: user_roles
-- Description: Many-to-many relationship between users and roles
-- =============================================
CREATE TABLE user_roles (
    user_id INT NOT NULL,
    role_id INT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: blacklisted_tokens
-- Description: JWT tokens that have been blacklisted (logout)
-- =============================================
CREATE TABLE blacklisted_tokens (
    id INT AUTO_INCREMENT PRIMARY KEY,
    token TEXT NOT NULL COMMENT 'JWT token',
    expiry_date TIMESTAMP NOT NULL COMMENT 'Token expiry date',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_expiry_date (expiry_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: password_reset_otps
-- Description: OTP codes for password reset
-- =============================================
CREATE TABLE password_reset_otps (
    id INT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(100) NOT NULL COMMENT 'User email',
    otp_code VARCHAR(6) NOT NULL COMMENT '6-digit OTP code',
    expiry_time TIMESTAMP NOT NULL COMMENT 'OTP expiry time',
    is_used BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'OTP used status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email (email),
    INDEX idx_otp_code (otp_code),
    INDEX idx_expiry_time (expiry_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: addresses
-- Description: User delivery addresses
-- =============================================
CREATE TABLE addresses (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    address_line VARCHAR(500) NOT NULL COMMENT 'Street address',
    ward VARCHAR(100) COMMENT 'Ward/Commune',
    district VARCHAR(100) COMMENT 'District',
    city VARCHAR(100) NOT NULL COMMENT 'City/Province',
    country VARCHAR(100) DEFAULT 'Vietnam' COMMENT 'Country',
    postal_code VARCHAR(20) COMMENT 'Postal code',
    latitude DECIMAL(10, 8) COMMENT 'GPS latitude',
    longitude DECIMAL(11, 8) COMMENT 'GPS longitude',
    address_type ENUM('home', 'work', 'other') DEFAULT 'other' COMMENT 'Address type',
    is_default BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Default address flag',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_is_default (is_default)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: categories
-- Description: Product categories
-- =============================================
CREATE TABLE categories (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE COMMENT 'Category name',
    description TEXT COMMENT 'Category description',
    image_url VARCHAR(500) COMMENT 'Category image',
    parent_id INT COMMENT 'Parent category ID (for subcategories)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Category active status',
    display_order INT DEFAULT 0 COMMENT 'Display order',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (parent_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_parent_id (parent_id),
    INDEX idx_is_active (is_active),
    INDEX idx_display_order (display_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: seller_approvals
-- Description: Seller registration approval requests
-- =============================================
CREATE TABLE seller_approvals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    business_name VARCHAR(200) NOT NULL COMMENT 'Business/Shop name',
    business_address TEXT NOT NULL COMMENT 'Business address',
    business_phone VARCHAR(20) NOT NULL COMMENT 'Business phone',
    tax_code VARCHAR(50) COMMENT 'Tax identification number',
    business_license_url VARCHAR(500) COMMENT 'Business license document URL',
    id_card_front_url VARCHAR(500) COMMENT 'ID card front image URL',
    id_card_back_url VARCHAR(500) COMMENT 'ID card back image URL',
    approval_status ENUM('pending', 'approved', 'rejected') NOT NULL DEFAULT 'pending' COMMENT 'Approval status',
    rejection_reason TEXT COMMENT 'Reason for rejection',
    approved_by INT COMMENT 'Admin who approved',
    approved_at TIMESTAMP COMMENT 'Approval timestamp',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (approved_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_approval_status (approval_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: shops
-- Description: Seller shops/stores
-- =============================================
CREATE TABLE shops (
    id INT AUTO_INCREMENT PRIMARY KEY,
    seller_id INT NOT NULL COMMENT 'Seller/Owner user ID',
    shop_name VARCHAR(200) NOT NULL COMMENT 'Shop name',
    description TEXT COMMENT 'Shop description',
    logo_url VARCHAR(500) COMMENT 'Shop logo URL',
    banner_url VARCHAR(500) COMMENT 'Shop banner URL',
    phone_number VARCHAR(20) COMMENT 'Contact phone',
    email VARCHAR(100) COMMENT 'Contact email',
    address_line VARCHAR(500) COMMENT 'Shop address',
    ward VARCHAR(100) COMMENT 'Ward',
    district VARCHAR(100) COMMENT 'District',
    city VARCHAR(100) COMMENT 'City',
    latitude DECIMAL(10, 8) COMMENT 'GPS latitude',
    longitude DECIMAL(11, 8) COMMENT 'GPS longitude',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Shop active status',
    rating DOUBLE DEFAULT 0.0 COMMENT 'Average rating',
    total_reviews BIGINT DEFAULT 0 COMMENT 'Total number of reviews',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (seller_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_seller_id (seller_id),
    INDEX idx_is_active (is_active),
    INDEX idx_rating (rating)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: products
-- Description: Products sold by shops
-- =============================================
CREATE TABLE products (
    id INT AUTO_INCREMENT PRIMARY KEY,
    shop_id INT NOT NULL,
    category_id INT COMMENT 'Product category',
    name VARCHAR(200) NOT NULL COMMENT 'Product name',
    description TEXT COMMENT 'Product description',
    image_urls TEXT COMMENT 'Product images (comma-separated URLs)',
    price DECIMAL(15, 2) NOT NULL COMMENT 'Original price',
    discount_price DECIMAL(15, 2) COMMENT 'Discounted price',
    stock_quantity INT NOT NULL DEFAULT 0 COMMENT 'Available stock',
    min_order_quantity INT DEFAULT 1 COMMENT 'Minimum order quantity',
    max_order_quantity INT DEFAULT 999 COMMENT 'Maximum order quantity',
    unit VARCHAR(50) DEFAULT 'piece' COMMENT 'Unit of measurement',
    weight DECIMAL(10, 2) COMMENT 'Weight (kg)',
    is_available BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Product availability',
    rating DOUBLE DEFAULT 0.0 COMMENT 'Average rating',
    total_reviews BIGINT DEFAULT 0 COMMENT 'Total reviews',
    total_sold INT DEFAULT 0 COMMENT 'Total units sold',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (category_id) REFERENCES categories(id) ON DELETE SET NULL,
    INDEX idx_shop_id (shop_id),
    INDEX idx_category_id (category_id),
    INDEX idx_is_available (is_available),
    INDEX idx_rating (rating),
    INDEX idx_price (price),
    FULLTEXT idx_name_description (name, description)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: variant_types
-- Description: Product variant types (e.g., Size, Color)
-- =============================================
CREATE TABLE variant_types (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    name VARCHAR(100) NOT NULL COMMENT 'Variant type name (e.g., Size, Color)',
    display_order INT DEFAULT 0 COMMENT 'Display order',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: product_variants
-- Description: Product variant options (e.g., Small, Medium, Large)
-- =============================================
CREATE TABLE product_variants (
    id INT AUTO_INCREMENT PRIMARY KEY,
    product_id INT NOT NULL,
    variant_type_id INT NOT NULL,
    variant_value VARCHAR(100) NOT NULL COMMENT 'Variant value (e.g., Small, Red)',
    price_adjustment DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Price adjustment for this variant',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Variant active status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    FOREIGN KEY (variant_type_id) REFERENCES variant_types(id) ON DELETE CASCADE,
    INDEX idx_product_id (product_id),
    INDEX idx_variant_type_id (variant_type_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: cart_items
-- Description: Shopping cart items
-- =============================================
CREATE TABLE cart_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL DEFAULT 1 COMMENT 'Item quantity',
    selected_variants JSON COMMENT 'Selected variants (JSON format)',
    special_instructions TEXT COMMENT 'Special instructions for this item',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: orders
-- Description: Customer orders
-- =============================================
CREATE TABLE orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_number VARCHAR(50) NOT NULL UNIQUE COMMENT 'Unique order number',
    user_id INT NOT NULL COMMENT 'Customer ID',
    shop_id INT NOT NULL COMMENT 'Shop ID',
    delivery_address_id INT NOT NULL COMMENT 'Delivery address',
    order_status ENUM('pending', 'confirmed', 'delivered', 'cancelled') NOT NULL DEFAULT 'pending' COMMENT 'Order status',
    payment_status ENUM('pending', 'paid', 'refunded') NOT NULL DEFAULT 'pending' COMMENT 'Payment status',
    payment_method ENUM('app_wallet', 'ship_cod') NOT NULL COMMENT 'Payment method',
    subtotal DECIMAL(15, 2) NOT NULL COMMENT 'Subtotal amount',
    delivery_fee DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Delivery fee',
    discount_amount DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Discount amount',
    tax_amount DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Tax amount',
    total_amount DECIMAL(15, 2) NOT NULL COMMENT 'Total amount',
    platform_commission_rate DECIMAL(5, 2) DEFAULT 12.00 COMMENT 'Platform commission rate (%)',
    platform_commission_amount DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Platform commission amount',
    seller_received_amount DECIMAL(15, 2) DEFAULT 0.00 COMMENT 'Amount seller receives',
    notes TEXT COMMENT 'Order notes',
    estimated_delivery_time TIMESTAMP COMMENT 'Estimated delivery time',
    actual_delivery_time TIMESTAMP COMMENT 'Actual delivery time',
    cancelled_reason TEXT COMMENT 'Cancellation reason',
    cancelled_at TIMESTAMP COMMENT 'Cancellation timestamp',
    cancelled_by INT COMMENT 'User who cancelled',
    confirmed_by INT COMMENT 'User who confirmed (seller)',
    confirmed_at TIMESTAMP COMMENT 'Confirmation timestamp',
    has_refund_request BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Order has review with refund request',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (shop_id) REFERENCES shops(id) ON DELETE CASCADE,
    FOREIGN KEY (delivery_address_id) REFERENCES addresses(id) ON DELETE RESTRICT,
    FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE SET NULL,
    FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_shop_id (shop_id),
    INDEX idx_order_number (order_number),
    INDEX idx_order_status (order_status),
    INDEX idx_payment_status (payment_status),
    INDEX idx_has_refund_request (has_refund_request),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: order_items
-- Description: Items in an order
-- =============================================
CREATE TABLE order_items (
    id INT AUTO_INCREMENT PRIMARY KEY,
    order_id INT NOT NULL,
    product_id INT NOT NULL,
    quantity INT NOT NULL COMMENT 'Ordered quantity',
    unit_price DECIMAL(15, 2) NOT NULL COMMENT 'Unit price at time of order',
    total_price DECIMAL(15, 2) NOT NULL COMMENT 'Total price for this item',
    selected_variants JSON COMMENT 'Selected variants (JSON format)',
    special_instructions TEXT COMMENT 'Special instructions',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: reviews
-- Description: Product and shop reviews
-- =============================================
CREATE TABLE reviews (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    reviewable_type ENUM('shop', 'product') NOT NULL COMMENT 'Type of entity being reviewed',
    reviewable_id INT NOT NULL COMMENT 'ID of shop or product',
    order_id INT COMMENT 'Related order ID',
    rating INT NOT NULL CHECK (rating BETWEEN 1 AND 5) COMMENT 'Rating (1-5 stars)',
    comment TEXT COMMENT 'Review comment',
    images JSON COMMENT 'Review images (JSON array)',
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Anonymous review flag',
    admin_reply TEXT COMMENT 'Shop owner reply (DEPRECATED)',
    admin_replied_at TIMESTAMP COMMENT 'Reply timestamp (DEPRECATED)',
    admin_replied_by INT COMMENT 'User who replied (DEPRECATED)',
    user_reply TEXT COMMENT 'User counter-reply (DEPRECATED)',
    user_replied_at TIMESTAMP COMMENT 'User reply timestamp (DEPRECATED)',
    is_approved BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Review approved status',
    has_refund_request BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'User requests refund',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE SET NULL,
    FOREIGN KEY (admin_replied_by) REFERENCES users(id) ON DELETE SET NULL,
    INDEX idx_user_id (user_id),
    INDEX idx_reviewable (reviewable_type, reviewable_id),
    INDEX idx_order_id (order_id),
    INDEX idx_rating (rating),
    INDEX idx_has_refund_request (has_refund_request),
    INDEX idx_is_approved (is_approved)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: review_replies
-- Description: Conversation thread for reviews (shop owner and user can reply)
-- =============================================
CREATE TABLE review_replies (
    id INT AUTO_INCREMENT PRIMARY KEY,
    review_id INT NOT NULL,
    user_id INT NOT NULL COMMENT 'User who replied',
    reply_text TEXT NOT NULL COMMENT 'Reply content',
    images JSON COMMENT 'Reply images (JSON array)',
    reply_type ENUM('SHOP_OWNER', 'USER') NOT NULL COMMENT 'Type of reply',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (review_id) REFERENCES reviews(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_review_id (review_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: wishlists
-- Description: User wishlist/favorites
-- =============================================
CREATE TABLE wishlists (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    product_id INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE CASCADE,
    UNIQUE KEY unique_user_product (user_id, product_id),
    INDEX idx_user_id (user_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: wallet_transactions
-- Description: User wallet transactions (deposit, payment, refund)
-- =============================================
CREATE TABLE wallet_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    transaction_type ENUM('DEPOSIT', 'PAYMENT', 'REFUND', 'COMMISSION') NOT NULL COMMENT 'Transaction type',
    amount DECIMAL(15, 2) NOT NULL COMMENT 'Transaction amount',
    balance_before DECIMAL(15, 2) NOT NULL COMMENT 'Balance before transaction',
    balance_after DECIMAL(15, 2) NOT NULL COMMENT 'Balance after transaction',
    description TEXT COMMENT 'Transaction description',
    reference_type VARCHAR(50) COMMENT 'Reference entity type (Order, MomoTransaction)',
    reference_id INT COMMENT 'Reference entity ID',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_transaction_type (transaction_type),
    INDEX idx_reference (reference_type, reference_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: momo_transactions
-- Description: MoMo payment transactions
-- =============================================
CREATE TABLE momo_transactions (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    order_id VARCHAR(50) NOT NULL UNIQUE COMMENT 'MoMo order ID',
    request_id VARCHAR(50) NOT NULL UNIQUE COMMENT 'MoMo request ID',
    amount BIGINT NOT NULL COMMENT 'Transaction amount',
    order_info VARCHAR(255) COMMENT 'Order information',
    result_code INT COMMENT 'MoMo result code',
    message VARCHAR(255) COMMENT 'Response message',
    pay_url TEXT COMMENT 'MoMo payment URL',
    transaction_status ENUM('PENDING', 'SUCCESS', 'FAILED') NOT NULL DEFAULT 'PENDING' COMMENT 'Transaction status',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_order_id (order_id),
    INDEX idx_transaction_status (transaction_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: conversations
-- Description: Chat conversations between users
-- =============================================
CREATE TABLE conversations (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user1_id INT NOT NULL COMMENT 'First participant',
    user2_id INT NOT NULL COMMENT 'Second participant',
    last_message_id INT COMMENT 'Last message in conversation',
    last_message_at TIMESTAMP COMMENT 'Timestamp of last message',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user1_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (user2_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY unique_conversation (user1_id, user2_id),
    INDEX idx_user1_id (user1_id),
    INDEX idx_user2_id (user2_id),
    INDEX idx_last_message_at (last_message_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: messages
-- Description: Chat messages
-- =============================================
CREATE TABLE messages (
    id INT AUTO_INCREMENT PRIMARY KEY,
    conversation_id INT NOT NULL,
    sender_id INT NOT NULL,
    receiver_id INT NOT NULL,
    message_text TEXT COMMENT 'Message content',
    message_type ENUM('TEXT', 'IMAGE', 'FILE') NOT NULL DEFAULT 'TEXT' COMMENT 'Message type',
    file_url VARCHAR(500) COMMENT 'File URL for images/files',
    is_read BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Message read status',
    read_at TIMESTAMP COMMENT 'Timestamp when message was read',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_sender_id (sender_id),
    INDEX idx_receiver_id (receiver_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: posts (Optional - if you have social features)
-- Description: User posts/feeds
-- =============================================
CREATE TABLE posts (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT NOT NULL,
    content TEXT NOT NULL COMMENT 'Post content',
    image_urls JSON COMMENT 'Post images (JSON array)',
    is_public BOOLEAN NOT NULL DEFAULT TRUE COMMENT 'Public post flag',
    likes_count INT DEFAULT 0 COMMENT 'Number of likes',
    comments_count INT DEFAULT 0 COMMENT 'Number of comments',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- TABLE: post_comments (Optional)
-- Description: Comments on posts
-- =============================================
CREATE TABLE post_comments (
    id INT AUTO_INCREMENT PRIMARY KEY,
    post_id INT NOT NULL,
    user_id INT NOT NULL,
    comment_text TEXT NOT NULL COMMENT 'Comment content',
    parent_comment_id INT COMMENT 'Parent comment for nested replies',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_comment_id) REFERENCES post_comments(id) ON DELETE CASCADE,
    INDEX idx_post_id (post_id),
    INDEX idx_user_id (user_id),
    INDEX idx_parent_comment_id (parent_comment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================
-- INSERT DEFAULT DATA
-- =============================================

-- Insert default roles
INSERT INTO roles (name, description) VALUES
('CUSTOMER', 'Regular customer role'),
('SELLER', 'Seller/Shop owner role'),
('ADMIN', 'Administrator role');

-- Insert default categories
INSERT INTO categories (name, description, is_active) VALUES
('Đồ ăn', 'Các món ăn đặc sản', TRUE),
('Đồ uống', 'Các loại đồ uống', TRUE),
('Bánh kẹo', 'Bánh ngọt và kẹo', TRUE),
('Đặc sản địa phương', 'Đặc sản từng vùng miền', TRUE),
('Hải sản', 'Hải sản tươi sống', TRUE),
('Rau củ quả', 'Rau củ quả sạch', TRUE);

-- =============================================
-- USEFUL QUERIES FOR MAINTENANCE
-- =============================================

-- View all tables
-- SHOW TABLES;

-- View table structure
-- DESCRIBE users;

-- Count records in each table
-- SELECT COUNT(*) FROM users;
-- SELECT COUNT(*) FROM shops;
-- SELECT COUNT(*) FROM products;
-- SELECT COUNT(*) FROM orders;

-- Check foreign key constraints
-- SELECT
--   TABLE_NAME,
--   COLUMN_NAME,
--   CONSTRAINT_NAME,
--   REFERENCED_TABLE_NAME,
--   REFERENCED_COLUMN_NAME
-- FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
-- WHERE TABLE_SCHEMA = 'foodtourapp'
-- AND REFERENCED_TABLE_NAME IS NOT NULL;

-- =============================================
-- END OF SCHEMA
-- =============================================

