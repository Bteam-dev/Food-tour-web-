-- Query 1 FIXED: Export hành vi mua hàng (SAFE CSV)
SEL
    o.user_id,
    oi.product_id,
    p.name as product_name,

    REPLACE(REPLACE(p.ingredients, ',', '|'), '"', '') AS ingredients,
    REPLACE(REPLACE(p.nutrition_info, ',', '|'), '"', '') AS nutrition_info,
    REPLACE(p.tags, ',', '|') AS tags,

    p.category_id,
    p.shop_id,
    oi.quantity,
    o.order_status,
    COALESCE(r.rating, 0) as user_rating,
    COUNT(*) OVER (PARTITION BY o.user_id, oi.product_id) as purchase_count

FROM orders o
         JOIN order_items oi ON o.id = oi.order_id
         JOIN products p ON oi.product_id = p.id
         LEFT JOIN reviews r ON r.order_id = o.id
    AND r.reviewable_id = oi.product_id
    AND r.reviewable_type = 'product'
WHERE o.order_status = 'delivered'
ORDER BY o.user_id, purchase_count DESC;

-- Query 2: Export wishlist
SELECT
    w.user_id,
    w.product_id,
    p.name,

    REPLACE(REPLACE(p.ingredients, ',', '|'), '"', '') AS ingredients,
    REPLACE(REPLACE(p.nutrition_info, ',', '|'), '"', '') AS nutrition_info,
    REPLACE(p.tags, ',', '|') AS tags,

    p.category_id,
    p.shop_id,
    p.rating as avg_rating

FROM wishlists w
         JOIN products p ON w.product_id = p.id;

-- Query 3 FIXED: Export products (SAFE CSV)
SELECT
    id,
    name,
    description,

    -- FIX 1: ingredients
    REPLACE(REPLACE(ingredients, ',', '|'), '"', '') AS ingredients,

    -- FIX 2: nutrition_info
    REPLACE(REPLACE(nutrition_info, ',', '|'), '"', '') AS nutrition_info,

    -- FIX 3: tags
    REPLACE(tags, ',', '|') AS tags,

    category_id,
    shop_id,
    rating,
    total_reviews,
    price

FROM products
WHERE is_available = true;