package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.OrderDTO.*;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.HereApiService;
import com.example.FoodTourApp.service.OrderService;
import com.example.FoodTourApp.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final VoucherRepository voucherRepository;
    private final WalletService walletService;
    private final FCMService fcmService;
    private final HereApiService hereApiService;
    private final ObjectMapper objectMapper;

    // ── Phí ship cố định (VND/km) và tối thiểu ──────────────────────────────
    private static final BigDecimal SHIP_FEE_PER_KM          = new BigDecimal("5000");   // 5 000 VND/km
    private static final BigDecimal SHIP_FEE_MIN              = new BigDecimal("15000");  // tối thiểu 15 000 VND
    private static final BigDecimal SHIP_FEE_FALLBACK         = new BigDecimal("15000");  // khi không tính được

    // ── Giới hạn khoảng cách giao hàng ──────────────────────────────────────
    private static final BigDecimal MAX_DELIVERY_DISTANCE_KM  = new BigDecimal("30");     // tối đa 30 km

    @Override
    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user) {
        log.info("Creating order for user: {}, cartItemIds: {}", user.getId(), request.getCartItemIds());

        // ── 1. Validate cart items ───────────────────────────────────────────
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        List<CartItem> cartItems = cartItemRepository.findAllById(request.getCartItemIds()).stream()
                .filter(item -> item.getCart().getId().equals(cart.getId()))
                .collect(Collectors.toList());

        if (cartItems.isEmpty())
            throw new RuntimeException("No valid cart items selected");
        if (cartItems.size() != request.getCartItemIds().size())
            throw new RuntimeException("One or more cart items are invalid or not owned by user");

        Set<String> distinctShops = cartItems.stream()
                .map(i -> i.getProduct().getShop().getShopName()).collect(Collectors.toSet());
        if (distinctShops.size() > 1)
            throw new RuntimeException("Chỉ được đặt hàng từ 1 shop trong 1 đơn hàng. Các shop: "
                    + String.join(", ", distinctShops));

        // ── 2. Normalize payment method ─────────────────────────────────────
        Order.PaymentMethod paymentMethod = normalizePaymentMethod(request.getPaymentMethod());

        // ── 3. Tạo Order, snapshot địa chỉ giao hàng ─────────────────────────
        Shop shop = cartItems.get(0).getProduct().getShop();

        // ── Kiểm tra shop đã được admin duyệt chưa ──────────────────────────
        if (!Boolean.TRUE.equals(shop.getIsVerified())) {
            throw new RuntimeException("Shop chưa được admin duyệt, không thể đặt hàng");
        }
        if (!Boolean.TRUE.equals(shop.getIsActive())) {
            throw new RuntimeException("Shop hiện không hoạt động, không thể đặt hàng");
        }

        // ── Kiểm tra giờ mở cửa shop ────────────────────────────────────────
        validateShopOpeningHours(shop);
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setUser(user);
        order.setShop(shop);
        order.setOrderStatus(Order.OrderStatus.pending);
        order.setPaymentStatus(Order.PaymentStatus.pending);
        order.setPaymentMethod(paymentMethod);
        order.setNotes(request.getNotes());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Snapshot địa chỉ giao hàng trực tiếp vào đơn (không lưu bảng addresses)
        AddressRequestDTO addr = request.getDeliveryAddress();
        order.setDeliveryAddressLine(addr.getAddressLine());
        order.setDeliveryWard(addr.getWard());
        order.setDeliveryDistrict(addr.getDistrict());
        order.setDeliveryCity(addr.getCity());
        order.setDeliveryCountry(addr.getCountry() != null ? addr.getCountry() : "Vietnam");
        order.setDeliveryPostalCode(addr.getPostalCode());
        order.setDeliveryLatitude(addr.getLatitude());
        order.setDeliveryLongitude(addr.getLongitude());

        // ── Kiểm tra khác tỉnh/thành phố (fail-fast trước khi gọi HERE API) ─
        if (!isSameCity(shop.getCity(), addr.getCity())) {
            throw new RuntimeException(String.format(
                "Địa chỉ giao hàng (%s) khác tỉnh/thành phố với shop (%s). " +
                "Ứng dụng chỉ hỗ trợ giao hàng nội thành, không khuyến khích đặt hàng đường xa.",
                addr.getCity(), shop.getCity()));
        }

        // ── 4. Tính phí ship theo khoảng cách (HERE Routing) ────────────────
        BigDecimal deliveryFee = calculateDeliveryFee(shop, addr, order);

        // ── Kiểm tra khoảng cách vượt giới hạn cho phép ─────────────────────
        BigDecimal distKm = order.getDeliveryDistanceKm();
        if (distKm != null && distKm.compareTo(MAX_DELIVERY_DISTANCE_KM) > 0) {
            throw new RuntimeException(String.format(
                "Khoảng cách giao hàng quá xa (%.1f km). " +
                "Chúng tôi chỉ hỗ trợ giao hàng trong vòng %s km để đảm bảo chất lượng món ăn. " +
                "Vui lòng chọn địa chỉ giao hàng gần hơn.",
                distKm.doubleValue(), MAX_DELIVERY_DISTANCE_KM.toPlainString()));
        }

        // ── 5. Tính subtotal ─────────────────────────────────────────────────
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            if (!cartItem.getProduct().getIsAvailable())
                throw new RuntimeException("Sản phẩm " + cartItem.getProduct().getName() + " không còn bán");
            if (cartItem.getQuantity() < cartItem.getProduct().getMinOrderQuantity() ||
                    cartItem.getQuantity() > cartItem.getProduct().getMaxOrderQuantity())
                throw new RuntimeException("Số lượng không hợp lệ: " + cartItem.getProduct().getName());

            BigDecimal effectivePrice = cartItem.getProduct().getEffectivePrice();
            BigDecimal itemTotal = effectivePrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            itemTotal = itemTotal.add(getVariantAdjustment(cartItem));
            subtotal = subtotal.add(itemTotal);
        }

        // ── 6. Áp dụng Voucher (nếu có) ─────────────────────────────────────
        BigDecimal discountAmount = BigDecimal.ZERO;
        if (request.getVoucherCode() != null && !request.getVoucherCode().isBlank()) {
            Voucher voucher = validateAndApplyVoucher(request.getVoucherCode(), shop, subtotal, deliveryFee, user);
            discountAmount = computeVoucherDiscount(voucher, subtotal, deliveryFee);
            // FREE_SHIP → discount = deliveryFee, bỏ phí ship
            if (voucher.getDiscountType() == Voucher.DiscountType.FREE_SHIP) {
                deliveryFee = BigDecimal.ZERO;
                discountAmount = BigDecimal.ZERO;   // phí ship đã = 0, không cộng thêm
            }
            voucher.setUsedCount(voucher.getUsedCount() + 1);
            voucherRepository.save(voucher);
            order.setVoucher(voucher);
            order.setVoucherCode(voucher.getCode());
        }

        BigDecimal taxAmount = subtotal.multiply(new BigDecimal("0.1")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = subtotal.add(deliveryFee).add(taxAmount).subtract(discountAmount);

        order.setSubtotal(subtotal);
        order.setDeliveryFee(deliveryFee);
        order.setTaxAmount(taxAmount);
        order.setDiscountAmount(discountAmount);
        order.setTotalAmount(totalAmount);
        order = orderRepository.save(order);

        // ── 7. Tạo OrderItems ────────────────────────────────────────────────
        for (CartItem cartItem : cartItems) {
            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(cartItem.getProduct());
            // ── Snapshot tên + ảnh tại thời điểm đặt ──────────────────────
            oi.setProductNameSnapshot(cartItem.getProduct().getName());
            oi.setProductImageUrlsSnapshot(cartItem.getProduct().getImageUrls());
            // ───────────────────────────────────────────────────────────────
            oi.setQuantity(cartItem.getQuantity());
            BigDecimal ep = cartItem.getProduct().getEffectivePrice();
            oi.setUnitPrice(ep);
            BigDecimal itemTotal = ep.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            try {
                Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
                List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
                for (ProductVariant v : variantRepository.findAllById(variantIds)) {
                    BigDecimal adj = v.getPriceAdjustment() != null ? v.getPriceAdjustment() : BigDecimal.ZERO;
                    itemTotal = itemTotal.add(adj.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                }
                oi.setSelectedVariants(cartItem.getSelectedVariants());
            } catch (Exception e) {
                log.warn("Cannot parse variants for cart item {}", cartItem.getId());
            }
            oi.setTotalPrice(itemTotal);
            oi.setSpecialInstructions(cartItem.getSpecialInstructions());
            orderItemRepository.save(oi);
        }

        // ── 8. Dọn cart + giảm tồn kho ───────────────────────────────────────
        cartItemRepository.deleteAll(cartItems);
        for (CartItem ci : cartItems) {
            Product p = ci.getProduct();
            p.setStockQuantity(Math.max(0, p.getStockQuantity() - ci.getQuantity()));
            productRepository.save(p);
        }

        // ── 9. Push notification ──────────────────────────────────────────────
        fcmService.sendOrderCreatedNotification(user, order.getOrderNumber(), shop.getShopName(), order.getTotalAmount());
        fcmService.sendNewOrderToSeller(shop.getSeller(), order.getOrderNumber(), user.getFullName(), order.getTotalAmount());

        log.info("Order {} created successfully, deliveryFee={}, distance={}km",
                order.getOrderNumber(), deliveryFee, order.getDeliveryDistanceKm());
        return mapToOrderResponseDTO(order);
    }

    // ── Kiểm tra cùng tỉnh/thành phố ─────────────────────────────────────────
    private boolean isSameCity(String city1, String city2) {
        if (city1 == null || city2 == null) return true; // Không xác định được → cho phép
        String n1 = normalizeCityName(city1);
        String n2 = normalizeCityName(city2);
        if (n1.isEmpty() || n2.isEmpty()) return true;
        // Dùng containment để xử lý "Ho Chi Minh" vs "Ho Chi Minh City"
        return n1.contains(n2) || n2.contains(n1);
    }

    private String normalizeCityName(String city) {
        if (city == null) return "";
        // Xử lý ký tự đ/Đ (không decompose qua NFD)
        String result = city.replace("đ", "d").replace("Đ", "d")
                            .toLowerCase().trim();
        // Xóa tiền tố loại địa danh
        result = result.replaceAll("^(thành phố |tp\\. |tp |tỉnh )", "");
        // NFD normalize → xóa dấu tổ hợp (ồ→o, ị→i, ả→a, ơ→o, ư→u, ...)
        String nfd = Normalizer.normalize(result, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                  .replaceAll("\\s+", " ").trim();
    }

    // ── Tính phí ship ─────────────────────────────────────────────────────────
    private BigDecimal calculateDeliveryFee(Shop shop, AddressRequestDTO dest, Order order) {
        // Chỉ tính được nếu cả shop và địa chỉ giao hàng đều có tọa độ
        if (shop.getLatitude() != null && shop.getLongitude() != null
                && dest.getLatitude() != null && dest.getLongitude() != null) {
            BigDecimal distKm = hereApiService.calculateRouteDistanceKm(
                    shop.getLatitude(), shop.getLongitude(),
                    dest.getLatitude(), dest.getLongitude());
            if (distKm != null) {
                order.setDeliveryDistanceKm(distKm);
                BigDecimal fee = SHIP_FEE_PER_KM.multiply(distKm).setScale(0, RoundingMode.CEILING);
                return fee.compareTo(SHIP_FEE_MIN) < 0 ? SHIP_FEE_MIN : fee;
            }
        }
        // Fallback: phí cố định
        return SHIP_FEE_FALLBACK;
    }

    // ── Tính tổng price adjustment từ variants ────────────────────────────────
    private BigDecimal getVariantAdjustment(CartItem cartItem) {
        try {
            Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
            List<Integer> ids = variantMap.getOrDefault("variantIds", List.of());
            List<ProductVariant> variants = variantRepository.findAllById(ids).stream()
                    .filter(v -> v.getProduct().getId().equals(cartItem.getProduct().getId()) && v.getIsActive())
                    .collect(Collectors.toList());
            if (ids.size() != variants.size())
                throw new RuntimeException("Variant không hợp lệ: " + cartItem.getProduct().getName());
            return variants.stream()
                    .map(v -> v.getPriceAdjustment() != null ? v.getPriceAdjustment() : BigDecimal.ZERO)
                    .map(adj -> adj.multiply(BigDecimal.valueOf(cartItem.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    // ── Validate & lấy Voucher ────────────────────────────────────────────────
    private Voucher validateAndApplyVoucher(String code, Shop shop,
                                             BigDecimal subtotal, BigDecimal deliveryFee, User user) {
        Voucher voucher = voucherRepository.findByCodeAndIsActiveTrue(code)
                .orElseThrow(() -> new RuntimeException("Mã voucher không hợp lệ hoặc đã hết hạn: " + code));

        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getStartDate()) || now.isAfter(voucher.getEndDate()))
            throw new RuntimeException("Mã voucher đã hết hạn");

        // Kiểm tra scope: SHOP voucher chỉ dùng cho đúng shop đó
        if (voucher.getScope() == Voucher.VoucherScope.SHOP
                && (voucher.getShop() == null || !voucher.getShop().getId().equals(shop.getId())))
            throw new RuntimeException("Mã voucher không áp dụng cho shop này");

        // Tổng giá trị đơn (subtotal + phí ship)
        BigDecimal orderValue = subtotal.add(deliveryFee);
        if (voucher.getMinOrderValue() != null && orderValue.compareTo(voucher.getMinOrderValue()) < 0)
            throw new RuntimeException("Đơn hàng chưa đủ " + voucher.getMinOrderValue() + " VND để áp mã");

        if (voucher.getMaxUsage() != null && voucher.getUsedCount() >= voucher.getMaxUsage())
            throw new RuntimeException("Mã voucher đã hết lượt sử dụng");

        // Kiểm tra per-user limit
        if (voucher.getMaxUsagePerUser() != null) {
            long userUsed = orderRepository.countByUserAndVoucherCode(user, code);
            if (userUsed >= voucher.getMaxUsagePerUser())
                throw new RuntimeException("Bạn đã dùng mã voucher này đủ số lần cho phép");
        }

        return voucher;
    }

    // ── Tính số tiền giảm từ Voucher ─────────────────────────────────────────
    private BigDecimal computeVoucherDiscount(Voucher voucher, BigDecimal subtotal, BigDecimal deliveryFee) {
        return switch (voucher.getDiscountType()) {
            case PERCENT -> {
                BigDecimal pct = voucher.getDiscountValue().divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
                BigDecimal raw = subtotal.multiply(pct).setScale(0, RoundingMode.CEILING);
                if (voucher.getMaxDiscountAmount() != null && raw.compareTo(voucher.getMaxDiscountAmount()) > 0)
                    yield voucher.getMaxDiscountAmount();
                yield raw;
            }
            case FIXED    -> voucher.getDiscountValue().min(subtotal);
            case FREE_SHIP -> deliveryFee;   // caller xử lý bằng cách set deliveryFee = 0
        };
    }

    // ── Normalize payment method ──────────────────────────────────────────────
    private Order.PaymentMethod normalizePaymentMethod(String raw) {
        String normalized = switch (raw.toUpperCase()) {
            case "WALLET", "APP_WALLET" -> "APP_WALLET";
            case "COD", "CASH", "SHIP_COD" -> "SHIP_COD";
            default -> throw new RuntimeException("Phương thức thanh toán không hợp lệ: " + raw);
        };
        return Order.PaymentMethod.valueOf(normalized.toLowerCase());
    }

    @Override
    @Transactional
    public OrderResponseDTO payOrder(Integer orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
        if (!order.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Bạn không có quyền thanh toán đơn hàng này");
        if (order.getPaymentStatus() == Order.PaymentStatus.paid)
            throw new RuntimeException("Đơn hàng đã được thanh toán rồi");
        if (order.getOrderStatus() == Order.OrderStatus.cancelled)
            throw new RuntimeException("Không thể thanh toán đơn hàng đã hủy");

        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            walletService.processOrderPayment(order);
            orderRepository.save(order);
            // Force-initialize seller entity within this transaction to avoid
            // Hibernate lazy-proxy issue when @Async thread accesses it outside session
            User seller = order.getShop().getSeller();
            seller.getId(); // triggers proxy initialization while session is still open
            seller.getFcmToken(); // eagerly read before async
            fcmService.sendOrderPaidNotification(user, order.getOrderNumber(), order.getShop().getShopName(), order.getTotalAmount());
            fcmService.sendNewOrderToSeller(seller, order.getOrderNumber(), user.getFullName(), order.getTotalAmount());
        } else {
            throw new RuntimeException("Đơn hàng COD sẽ thanh toán khi nhận hàng");
        }
        return mapToOrderResponseDTO(order);
    }

    @Override
    public Page<OrderResponseDTO> getUserOrders(User user, Pageable pageable) {
        return orderRepository.findByUser(user, pageable).map(this::mapToOrderResponseDTO);
    }

    @Override
    public OrderResponseDTO getOrderById(Integer orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Bạn không có quyền xem đơn hàng này");
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Integer orderId, User user) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        if (!order.getUser().getId().equals(user.getId()))
            throw new RuntimeException("Bạn không có quyền xóa đơn hàng này");
        if (order.getOrderStatus() != Order.OrderStatus.pending)
            throw new RuntimeException("Chỉ có thể hủy đơn hàng đang ở trạng thái pending");

        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet
                && order.getPaymentStatus() == Order.PaymentStatus.paid) {
            walletService.refundCancelledOrder(order);
        }
        order.setOrderStatus(Order.OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(user);
        order.setCancelledReason("Hủy bởi người dùng");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        restoreStock(order);
        fcmService.sendOrderStatusChangedToSeller(order.getShop().getSeller(),
                order.getOrderNumber(), user.getFullName(), "cancelled", "Hủy bởi người dùng");
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmOrder(Integer orderId, User seller) {
        Order order = getOrderForSeller(orderId, seller);
        if (order.getOrderStatus() != Order.OrderStatus.pending)
            throw new RuntimeException("Chỉ xác nhận được đơn pending");
        order.setOrderStatus(Order.OrderStatus.confirmed);
        order.setConfirmedBy(seller);
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        fcmService.sendOrderStatusChangedToBuyer(order.getUser(), order.getOrderNumber(),
                order.getShop().getShopName(), "confirmed", null);
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO markAsDelivered(Integer orderId, User seller) {
        Order order = getOrderForSeller(orderId, seller);
        if (order.getOrderStatus() != Order.OrderStatus.confirmed)
            throw new RuntimeException("Chỉ đánh dấu giao hàng được đơn confirmed");
        order.setOrderStatus(Order.OrderStatus.delivered);
        order.setActualDeliveryTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            // Wallet: giải phóng escrow → seller nhận sellerReceivedAmount
            walletService.releaseEscrowToSeller(order);
        } else if (order.getPaymentMethod() == Order.PaymentMethod.ship_cod
                && order.getPaymentStatus() != Order.PaymentStatus.paid) {
            // COD: thu commission từ ví seller
            walletService.processCommissionCOD(order);
            order.setPaymentStatus(Order.PaymentStatus.paid);
        }
        orderRepository.save(order);
        fcmService.sendOrderStatusChangedToBuyer(order.getUser(), order.getOrderNumber(),
                order.getShop().getShopName(), "delivered", null);
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmCODPayment(Integer orderId, User seller) {
        Order order = getOrderForSeller(orderId, seller);
        if (order.getPaymentMethod() != Order.PaymentMethod.ship_cod)
            throw new RuntimeException("Chỉ đơn COD mới cần xác nhận thanh toán");
        if (order.getPaymentStatus() == Order.PaymentStatus.paid)
            throw new RuntimeException("Đơn hàng đã thanh toán rồi");
        if (order.getOrderStatus() == Order.OrderStatus.cancelled)
            throw new RuntimeException("Không thể xác nhận đơn đã hủy");
        walletService.processCommissionCOD(order);
        order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setOrderStatus(Order.OrderStatus.delivered);
        order.setActualDeliveryTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        fcmService.sendOrderStatusChangedToBuyer(order.getUser(), order.getOrderNumber(),
                order.getShop().getShopName(), "delivered", null);
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO refundOrder(Integer orderId, User seller) {
        Order order = getOrderForSeller(orderId, seller);
        if (order.getPaymentStatus() != Order.PaymentStatus.paid)
            throw new RuntimeException("Chỉ hoàn tiền được đơn đã thanh toán");
        if (order.getPaymentStatus() == Order.PaymentStatus.refunded)
            throw new RuntimeException("Đơn đã được hoàn tiền rồi");
        walletService.refundDeliveredOrder(order);
        order.setPaymentStatus(Order.PaymentStatus.refunded);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        fcmService.sendOrderStatusChangedToBuyer(order.getUser(), order.getOrderNumber(),
                order.getShop().getShopName(), "refunded", null);
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrderBySeller(Integer orderId, User seller, String reason) {
        Order order = getOrderForSeller(orderId, seller);
        if (order.getOrderStatus() == Order.OrderStatus.cancelled)
            throw new RuntimeException("Đơn đã hủy rồi");
        if (order.getOrderStatus() == Order.OrderStatus.delivered)
            throw new RuntimeException("Không thể hủy đơn đã giao");
        if (order.getPaymentStatus() == Order.PaymentStatus.paid
                && order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            walletService.refundCancelledOrder(order);
        }
        order.setOrderStatus(Order.OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(seller);
        order.setCancelledReason(reason != null ? reason : "Hủy bởi seller");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        restoreStock(order);
        fcmService.sendOrderStatusChangedToBuyer(order.getUser(), order.getOrderNumber(),
                order.getShop().getShopName(), "cancelled", reason);
        return mapToOrderResponseDTO(order);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Order getOrderForSeller(Integer orderId, User seller) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));
        if (!order.getShop().getSeller().getId().equals(seller.getId()))
            throw new RuntimeException("Bạn không có quyền thao tác đơn hàng này");
        return order;
    }

    private void restoreStock(Order order) {
        for (OrderItem item : orderItemRepository.findByOrder(order)) {
            Product p = item.getProduct();
            p.setStockQuantity(p.getStockQuantity() + item.getQuantity());
            productRepository.save(p);
        }
    }

    /**
     * Kiểm tra shop có đang trong giờ mở cửa không.
     * openingHours JSON format:
     * {
     *   "monday":    {"open": "08:00", "close": "22:00", "closed": false},
     *   "tuesday":   {"open": "08:00", "close": "22:00", "closed": false},
     *   ...
     *   "sunday":    {"closed": true}
     * }
     * Nếu openingHours == null → không kiểm tra (shop mở cả ngày).
     */
    @SuppressWarnings("unchecked")
    private void validateShopOpeningHours(Shop shop) {
        String openingHoursJson = shop.getOpeningHours();
        if (openingHoursJson == null || openingHoursJson.isBlank()) {
            // Không cấu hình giờ → coi như mở cả ngày
            return;
        }
        try {
            Map<String, Object> hoursMap = objectMapper.readValue(openingHoursJson, Map.class);
            LocalDateTime now = LocalDateTime.now();
            DayOfWeek dayOfWeek = now.getDayOfWeek();

            // Chuyển DayOfWeek sang key tiếng Anh viết thường
            String dayKey = dayOfWeek.name().toLowerCase(); // e.g. "monday"

            Object dayObj = hoursMap.get(dayKey);
            if (dayObj == null) {
                // Không có cấu hình cho ngày này → coi như mở
                return;
            }

            Map<String, Object> dayConfig = (Map<String, Object>) dayObj;

            // Kiểm tra closed flag
            Object closedFlag = dayConfig.get("closed");
            if (Boolean.TRUE.equals(closedFlag)) {
                throw new RuntimeException("Shop đang đóng cửa hôm nay, vui lòng đặt hàng vào ngày khác");
            }

            String openStr  = (String) dayConfig.get("open");
            String closeStr = (String) dayConfig.get("close");

            if (openStr == null || closeStr == null) {
                // Thiếu cấu hình open/close → coi như mở
                return;
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm");
            LocalTime openTime  = LocalTime.parse(openStr,  fmt);
            LocalTime closeTime = LocalTime.parse(closeStr, fmt);
            LocalTime currentTime = now.toLocalTime();

            boolean isOpen;
            if (closeTime.isAfter(openTime)) {
                // Trường hợp bình thường: ví dụ 08:00 → 22:00
                isOpen = !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime);
            } else {
                // Qua nửa đêm: ví dụ 22:00 → 02:00
                isOpen = !currentTime.isBefore(openTime) || currentTime.isBefore(closeTime);
            }

            if (!isOpen) {
                throw new RuntimeException(
                    "Shop chưa mở cửa. Giờ hoạt động hôm nay: " + openStr + " - " + closeStr
                );
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Không thể parse openingHours của shop {}: {}", shop.getId(), e.getMessage());
            // Parse lỗi → không chặn đặt hàng
        }
    }

    // ── Pagination / filter methods (unchanged logic, reuse) ─────────────────

    // ── Helper: resolve danh sách shops theo shopId (null = tất cả) ─────────

    /**
     * Trả về danh sách shops của seller.
     * - shopId = null → tất cả shops
     * - shopId != null → đúng shop đó (phải thuộc seller, nếu không throw)
     */
    private List<Shop> resolveShops(User seller, Integer shopId) {
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) throw new RuntimeException("Bạn chưa có shop nào");
        if (shopId == null) return shops;
        return shops.stream()
                .filter(s -> s.getId().equals(shopId))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new RuntimeException("Shop không tồn tại hoặc không thuộc quyền quản lý của bạn"));
    }

    @Override
    public Page<OrderResponseDTO> getOrdersWithRefundRequests(User seller, Integer shopId, Pageable pageable) {
        List<Shop> shops = resolveShops(seller, shopId);
        return orderRepository.findByShopInAndHasRefundRequestTrue(shops, pageable)
                .map(this::mapToOrderResponseDTO);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrders(User seller, Integer shopId, Pageable pageable) {
        List<Shop> shops = resolveShops(seller, shopId);
        return orderRepository.findByShopIn(shops, pageable).map(this::mapToOrderResponseDTO);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrdersWithFilter(User seller, Integer shopId, Order.OrderStatus status,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        List<Shop> shops = resolveShops(seller, shopId);
        Page<Order> orders;
        if (status != null && startDate != null && endDate != null)
            orders = orderRepository.findByShopInAndOrderStatusAndCreatedAtBetween(shops, status, startDate, endDate, pageable);
        else if (status != null)
            orders = orderRepository.findByShopInAndOrderStatus(shops, status, pageable);
        else if (startDate != null && endDate != null)
            orders = orderRepository.findByShopInAndCreatedAtBetween(shops, startDate, endDate, pageable);
        else
            orders = orderRepository.findByShopIn(shops, pageable);
        return orders.map(this::mapToOrderResponseDTO);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrdersWithFilterByStatusString(User seller, Integer shopId, String status,
            LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Order.OrderStatus orderStatus = null;
        if (status != null && !status.isEmpty()) {
            try { orderStatus = Order.OrderStatus.valueOf(status.toLowerCase()); }
            catch (IllegalArgumentException e) { throw new RuntimeException("Trạng thái không hợp lệ: " + status); }
        }
        return getShopOrdersWithFilter(seller, shopId, orderStatus, startDate, endDate, pageable);
    }

    @Override
    public List<RevenueStatisticsDTO> getRevenueStatistics(User seller, Integer shopId, String periodType,
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Shop> shops = resolveShops(seller, shopId);
        List<Order> paidOrders = orderRepository.findPaidOrdersByShopsAndDateRange(shops, startDate, endDate);
        Map<String, List<Order>> grouped = new java.util.HashMap<>();
        for (Order o : paidOrders) {
            String period = switch (periodType.toLowerCase()) {
                case "day"   -> o.getCreatedAt().toLocalDate().toString();
                case "month" -> o.getCreatedAt().getYear() + "-" + String.format("%02d", o.getCreatedAt().getMonthValue());
                case "year"  -> String.valueOf(o.getCreatedAt().getYear());
                default      -> throw new RuntimeException("Invalid period type: phải là day, month hoặc year");
            };
            grouped.computeIfAbsent(period, k -> new java.util.ArrayList<>()).add(o);
        }
        List<RevenueStatisticsDTO> result = new java.util.ArrayList<>();
        grouped.forEach((period, orders) -> {
            RevenueStatisticsDTO stat = new RevenueStatisticsDTO();
            stat.setPeriod(period);
            stat.setTotalRevenue(orders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            stat.setTotalOrders((long) orders.size());
            stat.setPlatformCommission(orders.stream().map(o -> o.getPlatformCommissionAmount() != null ? o.getPlatformCommissionAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
            stat.setSellerRevenue(orders.stream().map(o -> o.getSellerReceivedAmount() != null ? o.getSellerReceivedAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add));
            result.add(stat);
        });
        result.sort(Comparator.comparing(RevenueStatisticsDTO::getPeriod));
        return result;
    }

    @Override
    public List<ProductSalesStatisticsDTO> getTopSellingProducts(User seller, Integer shopId,
            LocalDateTime startDate, LocalDateTime endDate, int limit) {
        List<Shop> shops = resolveShops(seller, shopId);
        List<Order> paidOrders = orderRepository.findPaidOrdersByShopsAndDateRange(shops, startDate, endDate);
        Map<Integer, ProductSalesStatisticsDTO> statsMap = new java.util.HashMap<>();
        Map<Integer, Long> orderCountMap = new java.util.HashMap<>();
        
        for (Order o : paidOrders) {
            for (OrderItem item : orderItemRepository.findByOrder(o)) {
                Integer pid = item.getProduct().getId();
                ProductSalesStatisticsDTO stat = statsMap.computeIfAbsent(pid, k -> {
                    ProductSalesStatisticsDTO s = new ProductSalesStatisticsDTO();
                    s.setProductId(pid); 
                    s.setProductName(item.getProduct().getName());
                    s.setTotalQuantitySold(0L); 
                    s.setTotalRevenue(BigDecimal.ZERO);
                    s.setSellerRevenue(BigDecimal.ZERO);
                    s.setTotalOrders(0L);
                    
                    // Set product image URL
                    if (item.getProduct().getImageUrls() != null && !item.getProduct().getImageUrls().trim().isEmpty()) {
                        try {
                            // Parse JSON array để lấy ảnh đầu tiên
                            String imageUrls = item.getProduct().getImageUrls().trim();
                            if (imageUrls.startsWith("[") && imageUrls.endsWith("]")) {
                                imageUrls = imageUrls.substring(1, imageUrls.length() - 1);
                                String[] urls = imageUrls.split(",");
                                if (urls.length > 0) {
                                    String firstUrl = urls[0].trim().replace("\"", "");
                                    if (!firstUrl.isEmpty()) {
                                        s.setProductImageUrl(firstUrl);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Ignore parsing error, just leave image URL null
                        }
                    }
                    
                    return s;
                });
                
                // Cập nhật số lượng bán và doanh thu tổng
                stat.setTotalQuantitySold(stat.getTotalQuantitySold() + item.getQuantity());
                stat.setTotalRevenue(stat.getTotalRevenue().add(item.getTotalPrice()));
                
                // Tính doanh thu seller nhận được từ order item này
                // Doanh thu seller = (item price / total order price) * seller received amount
                BigDecimal itemRatio = BigDecimal.ZERO;
                if (o.getTotalAmount() != null && o.getTotalAmount().compareTo(BigDecimal.ZERO) > 0) {
                    itemRatio = item.getTotalPrice().divide(o.getTotalAmount(), 4, BigDecimal.ROUND_HALF_UP);
                }
                
                BigDecimal sellerReceivedFromOrder = o.getSellerReceivedAmount() != null ? o.getSellerReceivedAmount() : BigDecimal.ZERO;
                BigDecimal itemSellerRevenue = itemRatio.multiply(sellerReceivedFromOrder);
                stat.setSellerRevenue(stat.getSellerRevenue().add(itemSellerRevenue));
                
                // Đếm số đơn hàng duy nhất cho mỗi sản phẩm
                orderCountMap.merge(pid, 1L, (oldVal, newVal) -> {
                    // Chỉ tăng nếu order này chưa được đếm cho sản phẩm này
                    return oldVal;
                });
            }
        }
        
        // Set số đơn hàng cho từng sản phẩm
        for (ProductSalesStatisticsDTO stat : statsMap.values()) {
            Set<Integer> uniqueOrderIds = new HashSet<>();
            for (Order o : paidOrders) {
                for (OrderItem item : orderItemRepository.findByOrder(o)) {
                    if (item.getProduct().getId().equals(stat.getProductId())) {
                        uniqueOrderIds.add(o.getId());
                    }
                }
            }
            stat.setTotalOrders((long) uniqueOrderIds.size());
        }
        
        return statsMap.values().stream()
                .sorted(Comparator.comparing(ProductSalesStatisticsDTO::getTotalQuantitySold).reversed())
                .limit(limit).collect(Collectors.toList());
    }

    @Override
    public DashboardStatisticsDTO getDashboardStatistics(User seller, Integer shopId,
            LocalDateTime startDate, LocalDateTime endDate) {
        List<Shop> shops = resolveShops(seller, shopId);

        DashboardStatisticsDTO dashboard = new DashboardStatisticsDTO();

        // Set thông tin scope
        if (shopId != null && shops.size() == 1) {
            dashboard.setShopId(shops.get(0).getId());
            dashboard.setShopName(shops.get(0).getShopName());
        }
        // shopId = null → shopId và shopName trong DTO vẫn null (tổng hợp tất cả)

        long totalOrders      = orderRepository.countByShopIn(shops);
        long pendingOrders    = orderRepository.countByShopInAndOrderStatus(shops, Order.OrderStatus.pending);
        long completedOrders  = orderRepository.countByShopInAndOrderStatus(shops, Order.OrderStatus.delivered);
        long cancelledOrders  = orderRepository.countByShopInAndOrderStatus(shops, Order.OrderStatus.cancelled);

        List<Order> paidOrders = orderRepository.findPaidOrdersByShopsAndDateRange(shops, startDate, endDate);
        BigDecimal totalRevenue      = paidOrders.stream().map(Order::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal platformCommission = paidOrders.stream().map(o -> o.getPlatformCommissionAmount() != null ? o.getPlatformCommissionAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sellerRevenue      = paidOrders.stream().map(o -> o.getSellerReceivedAmount() != null ? o.getSellerReceivedAmount() : BigDecimal.ZERO).reduce(BigDecimal.ZERO, BigDecimal::add);

        dashboard.setTotalOrders(totalOrders);
        dashboard.setPendingOrders(pendingOrders);
        dashboard.setCompletedOrders(completedOrders);
        dashboard.setCancelledOrders(cancelledOrders);
        dashboard.setTotalRevenue(totalRevenue);
        dashboard.setPlatformCommission(platformCommission);
        dashboard.setSellerRevenue(sellerRevenue);
        dashboard.setTopSellingProducts(getTopSellingProducts(seller, shopId, startDate, endDate, 10));
        return dashboard;
    }

    // ── mapToOrderResponseDTO ─────────────────────────────────────────────────
    private OrderResponseDTO mapToOrderResponseDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setUserId(order.getUser().getId());
        dto.setUserName(order.getUser().getFullName());
        dto.setShopId(order.getShop().getId());
        dto.setShopName(order.getShop().getShopName());
        dto.setOrderStatus(order.getOrderStatus().name());
        dto.setPaymentStatus(order.getPaymentStatus().name());
        dto.setPaymentMethod(order.getPaymentMethod().name());
        dto.setSubtotal(order.getSubtotal());
        dto.setDeliveryFee(order.getDeliveryFee());
        dto.setDiscountAmount(order.getDiscountAmount());
        dto.setTaxAmount(order.getTaxAmount());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPlatformCommissionRate(order.getPlatformCommissionRate());
        dto.setPlatformCommissionAmount(order.getPlatformCommissionAmount());
        dto.setSellerReceivedAmount(order.getSellerReceivedAmount());
        dto.setNotes(order.getNotes());
        dto.setEstimatedDeliveryTime(order.getEstimatedDeliveryTime());
        dto.setActualDeliveryTime(order.getActualDeliveryTime());
        dto.setCancelledReason(order.getCancelledReason());
        dto.setCancelledAt(order.getCancelledAt());
        if (order.getCancelledBy() != null) {
            dto.setCancelledById(order.getCancelledBy().getId());
            dto.setCancelledByName(order.getCancelledBy().getFullName());
        }
        dto.setCreatedAt(order.getCreatedAt());
        dto.setUpdatedAt(order.getUpdatedAt());
        dto.setHasRefundRequest(order.getHasRefundRequest());
        dto.setDeliveryDistanceKm(order.getDeliveryDistanceKm());
        dto.setVoucherCode(order.getVoucherCode());

        // Build delivery address từ các trường nhúng
        AddressResponseDTO addrDto = new AddressResponseDTO();
        addrDto.setAddressLine(order.getDeliveryAddressLine());
        addrDto.setWard(order.getDeliveryWard());
        addrDto.setDistrict(order.getDeliveryDistrict());
        addrDto.setCity(order.getDeliveryCity());
        addrDto.setCountry(order.getDeliveryCountry());
        addrDto.setPostalCode(order.getDeliveryPostalCode());
        addrDto.setLatitude(order.getDeliveryLatitude());
        addrDto.setLongitude(order.getDeliveryLongitude());
        StringBuilder full = new StringBuilder();
        if (order.getDeliveryAddressLine() != null) full.append(order.getDeliveryAddressLine());
        if (order.getDeliveryWard() != null) full.append(", ").append(order.getDeliveryWard());
        if (order.getDeliveryDistrict() != null) full.append(", ").append(order.getDeliveryDistrict());
        if (order.getDeliveryCity() != null) full.append(", ").append(order.getDeliveryCity());
        addrDto.setFullAddress(full.toString());
        dto.setDeliveryAddress(addrDto);

        List<OrderItem> items = orderItemRepository.findByOrder(order);
        dto.setOrderItems(items.stream().map(this::mapToOrderItemResponseDTO).collect(Collectors.toList()));
        return dto;
    }

    private OrderItemResponseDTO mapToOrderItemResponseDTO(OrderItem oi) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(oi.getId());

        // ── Dùng snapshot trước, fallback sang FK nếu product chưa bị xóa ──
        dto.setProductId(oi.getProduct() != null ? oi.getProduct().getId() : null);
        dto.setProductName(
            oi.getProductNameSnapshot() != null
                ? oi.getProductNameSnapshot()
                : (oi.getProduct() != null ? oi.getProduct().getName() : "[Sản phẩm không còn tồn tại]")
        );

        String imageJson = oi.getProductImageUrlsSnapshot() != null
                ? oi.getProductImageUrlsSnapshot()
                : (oi.getProduct() != null ? oi.getProduct().getImageUrls() : null);

        if (imageJson != null && !imageJson.isBlank()) {
            try {
                dto.setImageUrls(objectMapper.readValue(imageJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {}));
            } catch (Exception e) {
                log.warn("Failed to parse imageUrls JSON for order item {}: {}", oi.getId(), e.getMessage());
                dto.setImageUrls(List.of());
            }
        } else {
            dto.setImageUrls(List.of());
        }
        // ──────────────────────────────────────────────────────────────────

        dto.setQuantity(oi.getQuantity());
        dto.setUnitPrice(oi.getUnitPrice());
        dto.setTotalPrice(oi.getTotalPrice());
        try {
            @SuppressWarnings("unchecked")
            Map<String, List<Integer>> vm = objectMapper.readValue(oi.getSelectedVariants(), Map.class);
            List<Integer> ids = vm.getOrDefault("variantIds", List.of());
            dto.setSelectedVariants(variantRepository.findAllById(ids).stream()
                    .map(this::mapToVariantResponseDTO).toList());
        } catch (Exception e) {
            dto.setSelectedVariants(List.of());
        }
        dto.setSpecialInstructions(oi.getSpecialInstructions());
        return dto;
    }

    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant v) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(v.getId());
        dto.setVariantTypeId(v.getVariantType().getId());
        dto.setVariantTypeName(v.getVariantType().getName());
        dto.setVariantValue(v.getVariantValue());
        dto.setPriceAdjustment(v.getPriceAdjustment());
        dto.setIsActive(v.getIsActive());
        return dto;
    }
}
