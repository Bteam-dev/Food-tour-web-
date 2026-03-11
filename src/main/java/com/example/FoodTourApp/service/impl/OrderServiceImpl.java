package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.DashboardStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderItemResponseDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.DTO.OrderDTO.ProductSalesStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.RevenueStatisticsDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.FCMService;
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
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final CartRepository cartRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository variantRepository;
    private final ProductRepository productRepository;
    private final ShopRepository shopRepository;
    private final WalletService walletService;
    private final FCMService fcmService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user) {
        log.info("Creating order for user: {}, cartItemIds: {}, paymentMethod: {}",
                user.getId(), request.getCartItemIds(), request.getPaymentMethod());

        // Lấy cart của user
        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        // Lấy các cart item thuộc cart của user
        List<CartItem> cartItems = cartItemRepository.findAllById(request.getCartItemIds()).stream()
                .filter(item -> item.getCart().getId().equals(cart.getId()))
                .collect(Collectors.toList());

        if (cartItems.isEmpty()) {
            throw new RuntimeException("No valid cart items selected");
        }

        if (cartItems.size() != request.getCartItemIds().size()) {
            throw new RuntimeException("One or more cart items are invalid or not owned by user");
        }

        // Kiểm tra tất cả CartItem thuộc cùng shop
        Integer shopId = cartItems.get(0).getProduct().getShop().getId();
        String shopName = cartItems.get(0).getProduct().getShop().getShopName();

        Set<String> distinctShopNames = cartItems.stream()
                .map(item -> item.getProduct().getShop().getShopName())
                .collect(Collectors.toSet());

        if (distinctShopNames.size() > 1) {
            throw new RuntimeException(
                "Chỉ được đặt hàng từ 1 shop trong 1 đơn hàng. " +
                "Các món bạn chọn thuộc " + distinctShopNames.size() + " shop khác nhau: " +
                String.join(", ", distinctShopNames) + ". " +
                "Vui lòng chọn lại chỉ các món cùng 1 shop."
            );
        }

        // Kiểm tra payment method và normalize
        Order.PaymentMethod paymentMethod;
        try {
            // ✅ Normalize payment method từ client
            String normalizedMethod = request.getPaymentMethod().toUpperCase();

            // Map các tên thân thiện sang tên enum - CHỈ WALLET VÀ COD
            switch (normalizedMethod) {
                case "WALLET":
                case "APP_WALLET":
                    normalizedMethod = "APP_WALLET";
                    break;
                case "COD":
                case "CASH":
                case "SHIP_COD":
                    normalizedMethod = "SHIP_COD";
                    break;
                default:
                    throw new RuntimeException("Invalid payment method: " + request.getPaymentMethod() +
                            ". Only WALLET (app wallet) and COD (cash on delivery) are allowed.");
            }

            paymentMethod = Order.PaymentMethod.valueOf(normalizedMethod.toLowerCase());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid payment method: " + request.getPaymentMethod() +
                ". Only WALLET (app wallet) and COD (cash on delivery) are allowed.");
        }

        // ✅ Tạo địa chỉ giao hàng từ request (user luôn nhập địa chỉ mới)
        Address deliveryAddress = createAddressFromDTO(request.getDeliveryAddress(), user);
        deliveryAddress = addressRepository.save(deliveryAddress);

        // Tạo đơn hàng
        Order order = new Order();
        order.setOrderNumber(UUID.randomUUID().toString());
        order.setUser(user);
        order.setShop(cartItems.get(0).getProduct().getShop());
        order.setDeliveryAddress(deliveryAddress);
        order.setOrderStatus(Order.OrderStatus.pending);
        order.setPaymentStatus(Order.PaymentStatus.pending);
        order.setPaymentMethod(paymentMethod); // ✅ Dùng biến đã normalize
        order.setNotes(request.getNotes());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Tính toán giá
        BigDecimal subtotal = BigDecimal.ZERO;
        for (CartItem cartItem : cartItems) {
            if (!cartItem.getProduct().getIsAvailable()) {
                throw new RuntimeException("Product " + cartItem.getProduct().getName() + " is not available");
            }
            if (cartItem.getQuantity() < cartItem.getProduct().getMinOrderQuantity() ||
                    cartItem.getQuantity() > cartItem.getProduct().getMaxOrderQuantity()) {
                throw new RuntimeException("Invalid quantity for product " + cartItem.getProduct().getName());
            }

            // TÍNH THEO GIÁ HIỆU LỰC (ưu tiên discountPrice nếu có)
            BigDecimal effectivePrice = cartItem.getProduct().getEffectivePrice();
            BigDecimal itemTotal = effectivePrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            try {
                Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
                List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
                List<ProductVariant> variants = variantRepository.findAllById(variantIds).stream()
                        .filter(v -> v.getProduct().getId().equals(cartItem.getProduct().getId()) && v.getIsActive())
                        .collect(Collectors.toList());
                if (variantIds.size() != variants.size()) {
                    throw new RuntimeException("One or more variants for product " + cartItem.getProduct().getName() + " are invalid");
                }
                for (ProductVariant variant : variants) {
                    BigDecimal adjustment = variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : BigDecimal.ZERO;
                    itemTotal = itemTotal.add(adjustment.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                }
            } catch (Exception e) {
                log.error("Error deserializing variants for cartItem: {}: {}", cartItem.getId(), e.getMessage());
                throw new RuntimeException("Error processing variants for product " + cartItem.getProduct().getName());
            }
            subtotal = subtotal.add(itemTotal);
        }

        order.setSubtotal(subtotal);
        order.setDeliveryFee(new BigDecimal("15000.00"));
        order.setTaxAmount(subtotal.multiply(new BigDecimal("0.1")).setScale(2, RoundingMode.HALF_UP));
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setTotalAmount(subtotal.add(order.getDeliveryFee()).add(order.getTaxAmount()).subtract(order.getDiscountAmount()));

        order = orderRepository.save(order);

        // Tạo OrderItem từ CartItem
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());

            // LƯU GIÁ HIỆU LỰC VÀO ORDER ITEM (để sau này vẫn đúng nếu giá thay đổi)
            BigDecimal effectivePrice = cartItem.getProduct().getEffectivePrice();
            orderItem.setUnitPrice(effectivePrice);
            BigDecimal itemTotal = effectivePrice.multiply(BigDecimal.valueOf(cartItem.getQuantity()));
            try {
                Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
                List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
                List<ProductVariant> variants = variantRepository.findAllById(variantIds);
                for (ProductVariant variant : variants) {
                    BigDecimal adjustment = variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : BigDecimal.ZERO;
                    itemTotal = itemTotal.add(adjustment.multiply(BigDecimal.valueOf(cartItem.getQuantity())));
                }
                orderItem.setSelectedVariants(cartItem.getSelectedVariants());
            } catch (Exception e) {
                log.error("Error deserializing variants for cartItem: {}: {}", cartItem.getId(), e.getMessage());
            }
            orderItem.setTotalPrice(itemTotal);
            orderItem.setSpecialInstructions(cartItem.getSpecialInstructions());
            orderItemRepository.save(orderItem);
        }

        // ===== KHÔNG THANH TOÁN NGAY - CHỈ TẠO ĐƠN HÀNG =====
        // Đơn hàng sẽ ở trạng thái pending cho đến khi user gọi API thanh toán
        log.info("Order created with pending payment status. User can pay later.");

        // Xóa các CartItem được chọn
        cartItemRepository.deleteAll(cartItems);

        // ===== GIẢM TỒN KHO KHI ĐẶT HÀNG =====
        for (CartItem cartItem : cartItems) {
            Product product = cartItem.getProduct();
            int newStock = product.getStockQuantity() - cartItem.getQuantity();
            if (newStock < 0) newStock = 0;
            product.setStockQuantity(newStock);
            productRepository.save(product);
            log.info("Stock decreased for product {}: -{} => {}", product.getId(), cartItem.getQuantity(), newStock);
        }

        log.info("Order created successfully: orderId: {}, paymentStatus: {}",
                order.getId(), order.getPaymentStatus());

        // 🔔 Push: thông báo đặt hàng thành công cho người mua
        fcmService.sendOrderCreatedNotification(
                user,
                order.getOrderNumber(),
                order.getShop().getShopName(),
                order.getTotalAmount()
        );

        // 🔔 Push: thông báo có đơn mới cho seller
        fcmService.sendNewOrderToSeller(
                order.getShop().getSeller(),
                order.getOrderNumber(),
                user.getFullName(),
                order.getTotalAmount()
        );

        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO payOrder(Integer orderId, User user) {
        log.info("User {} is paying for order {}", user.getId(), orderId);

        // Lấy đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu
        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Bạn không có quyền thanh toán đơn hàng này");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getPaymentStatus() == Order.PaymentStatus.paid) {
            throw new RuntimeException("Đơn hàng đã được thanh toán rồi");
        }

        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Không thể thanh toán đơn hàng đã hủy");
        }

        // Xử lý thanh toán theo phương thức
        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            try {
                log.info("Processing wallet payment for order {}", order.getId());
                walletService.processOrderPayment(order);
                orderRepository.save(order);
                log.info("Wallet payment successful for order {}", order.getId());

                // 🔔 Push: thanh toán thành công (wallet notification gửi từ WalletServiceImpl,
                // đây gửi thêm order-level notification cho người mua)
                fcmService.sendOrderPaidNotification(
                        user,
                        order.getOrderNumber(),
                        order.getShop().getShopName(),
                        order.getTotalAmount()
                );

                // 🔔 Push: seller nhận đơn mới đã thanh toán
                fcmService.sendNewOrderToSeller(
                        order.getShop().getSeller(),
                        order.getOrderNumber(),
                        user.getFullName(),
                        order.getTotalAmount()
                );
            } catch (Exception e) {
                log.error("Wallet payment failed for order {}: {}", order.getId(), e.getMessage());
                throw new RuntimeException("Thanh toán thất bại: " + e.getMessage());
            }
        } else if (order.getPaymentMethod() == Order.PaymentMethod.ship_cod) {
            log.info("Order {} is COD, no immediate payment required", order.getId());
            throw new RuntimeException("Đơn hàng COD sẽ thanh toán khi nhận hàng");
        } else {
            throw new RuntimeException("Phương thức thanh toán không được hỗ trợ");
        }

        return mapToOrderResponseDTO(order);
    }

    @Override
    public Page<OrderResponseDTO> getUserOrders(User user, Pageable pageable) {
        log.info("Getting orders for user: {} with pagination", user.getId());
        Page<Order> orders = orderRepository.findByUser(user, pageable);
        return orders.map(this::mapToOrderResponseDTO);
    }

    @Override
    public OrderResponseDTO getOrderById(Integer orderId, User user) {
        log.info("Getting order {} for user: {}", orderId, user.getId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to view this order");
        }

        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public void deleteOrder(Integer orderId, User user) {
        log.info("Deleting order: {} for user: {}", orderId, user.getId());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        if (!order.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("You do not have permission to delete this order");
        }

        if (order.getOrderStatus() != Order.OrderStatus.pending) {
            throw new RuntimeException("Only orders with 'pending' status can be deleted");
        }

        // Hoàn tiền nếu đơn hàng đã thanh toán qua ví
        if (order.getPaymentMethod() == Order.PaymentMethod.app_wallet
                && order.getPaymentStatus() == Order.PaymentStatus.paid) {
            try {
                log.info("Refunding wallet payment for cancelled order {}", orderId);
                walletService.refundOrder(order);
            } catch (Exception e) {
                log.error("Failed to refund order {}: {}", orderId, e.getMessage());
                throw new RuntimeException("Không thể hoàn tiền: " + e.getMessage());
            }
        }

        order.setOrderStatus(Order.OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(user);
        order.setCancelledReason("Cancelled by user");
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // ===== HOÀN LẠI TỒN KHO KHI USER HỦY ĐƠN =====
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            int restoredStock = product.getStockQuantity() + item.getQuantity();
            product.setStockQuantity(restoredStock);
            productRepository.save(product);
            log.info("Stock restored for product {}: +{} => {}", product.getId(), item.getQuantity(), restoredStock);
        }

        // 🔔 Push: thông báo hủy đơn cho seller
        fcmService.sendOrderStatusChangedToSeller(
                order.getShop().getSeller(),
                order.getOrderNumber(),
                user.getFullName(),
                "cancelled",
                "Cancelled by user"
        );

        log.info("Order deleted (soft delete) successfully: {}", orderId);
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmOrder(Integer orderId, User seller) {
        log.info("Seller {} is confirming order {}", seller.getId(), orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu
        if (!order.getShop().getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Bạn không có quyền xác nhận đơn hàng này");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getOrderStatus() != Order.OrderStatus.pending) {
            throw new RuntimeException("Chỉ có thể xác nhận đơn hàng đang ở trạng thái pending");
        }

        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Không thể xác nhận đơn hàng đã hủy");
        }

        // Chuyển trạng thái sang confirmed
        order.setOrderStatus(Order.OrderStatus.confirmed);
        order.setConfirmedBy(seller);
        order.setConfirmedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // 🔔 Push: thông báo đơn đã được xác nhận cho người mua
        fcmService.sendOrderStatusChangedToBuyer(
                order.getUser(),
                order.getOrderNumber(),
                order.getShop().getShopName(),
                "confirmed",
                null
        );

        log.info("Order {} confirmed successfully by seller {}", orderId, seller.getId());
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO markAsDelivered(Integer orderId, User seller) {
        log.info("Seller {} is marking order {} as delivered", seller.getId(), orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu
        if (!order.getShop().getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Bạn không có quyền cập nhật đơn hàng này");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getOrderStatus() != Order.OrderStatus.confirmed) {
            throw new RuntimeException("Chỉ có thể đánh dấu đã giao hàng cho đơn hàng đang ở trạng thái confirmed");
        }

        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Không thể cập nhật đơn hàng đã hủy");
        }

        // Chuyển trạng thái sang delivered
        order.setOrderStatus(Order.OrderStatus.delivered);
        order.setActualDeliveryTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Nếu là COD và chưa thanh toán, tự động xác nhận thanh toán
        if (order.getPaymentMethod() == Order.PaymentMethod.ship_cod
                && order.getPaymentStatus() != Order.PaymentStatus.paid) {

            // Tính toán hoa hồng cho platform (12%)
            BigDecimal commissionRate = order.getPlatformCommissionRate() != null
                    ? order.getPlatformCommissionRate()
                    : new BigDecimal("12.00");
            BigDecimal commissionAmount = order.getTotalAmount()
                    .multiply(commissionRate)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal sellerReceiveAmount = order.getTotalAmount().subtract(commissionAmount);

            order.setPlatformCommissionRate(commissionRate);
            order.setPlatformCommissionAmount(commissionAmount);
            order.setSellerReceivedAmount(sellerReceiveAmount);
            order.setPaymentStatus(Order.PaymentStatus.paid);

            log.info("COD payment automatically confirmed for order {}. Total: {}, Commission: {}, Seller receives: {}",
                    order.getId(), order.getTotalAmount(), commissionAmount, sellerReceiveAmount);
        }

        orderRepository.save(order);

        // 🔔 Push: thông báo đã giao hàng cho người mua
        fcmService.sendOrderStatusChangedToBuyer(
                order.getUser(),
                order.getOrderNumber(),
                order.getShop().getShopName(),
                "delivered",
                null
        );

        log.info("Order {} marked as delivered successfully", orderId);
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO confirmCODPayment(Integer orderId, User seller) {
        log.info("Seller {} is confirming COD payment for order {}", seller.getId(), orderId);

        // Lấy đơn hàng
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu (phải là seller của shop)
        if (!order.getShop().getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Bạn không có quyền xác nhận đơn hàng này");
        }

        // Kiểm tra phương thức thanh toán
        if (order.getPaymentMethod() != Order.PaymentMethod.ship_cod) {
            throw new RuntimeException("Chỉ đơn hàng COD mới cần xác nhận thanh toán");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getPaymentStatus() == Order.PaymentStatus.paid) {
            throw new RuntimeException("Đơn hàng đã được thanh toán rồi");
        }

        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Không thể xác nhận đơn hàng đã hủy");
        }

        // Xử lý COD: Không trừ tiền wallet, chỉ cập nhật trạng thái
        // Tính toán hoa hồng cho platform (12%)
        BigDecimal commissionRate = order.getPlatformCommissionRate() != null
                ? order.getPlatformCommissionRate()
                : new BigDecimal("12.00");
        BigDecimal commissionAmount = order.getTotalAmount()
                .multiply(commissionRate)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal sellerReceiveAmount = order.getTotalAmount().subtract(commissionAmount);

        // Lưu thông tin hoa hồng vào đơn hàng
        order.setPlatformCommissionRate(commissionRate);
        order.setPlatformCommissionAmount(commissionAmount);
        order.setSellerReceivedAmount(sellerReceiveAmount);

        log.info("Order {} COD confirmed. Total: {}, Commission: {} ({}%), Seller receives: {}",
                order.getId(), order.getTotalAmount(), commissionAmount, commissionRate, sellerReceiveAmount);

        // Cập nhật trạng thái thanh toán
        order.setPaymentStatus(Order.PaymentStatus.paid);
        order.setOrderStatus(Order.OrderStatus.delivered); // Tự động chuyển sang đã giao hàng
        order.setActualDeliveryTime(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // 🔔 Push: thông báo đã giao COD cho người mua
        fcmService.sendOrderStatusChangedToBuyer(
                order.getUser(),
                order.getOrderNumber(),
                order.getShop().getShopName(),
                "delivered",
                null
        );

        log.info("COD payment confirmed successfully for order {}.", order.getId());
        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO refundOrder(Integer orderId, User seller) {
        log.info("Seller {} is processing refund for order {}", seller.getId(), orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu
        if (!order.getShop().getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Bạn không có quyền hoàn tiền đơn hàng này");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Đơn hàng đã bị hủy");
        }

        if (order.getPaymentStatus() != Order.PaymentStatus.paid) {
            throw new RuntimeException("Chỉ có thể hoàn tiền cho đơn hàng đã thanh toán");
        }

        if (order.getPaymentStatus() == Order.PaymentStatus.refunded) {
            throw new RuntimeException("Đơn hàng đã được hoàn tiền rồi");
        }

        // Xử lý hoàn tiền qua WalletService
        try {
            walletService.refundOrder(order);

            // Cập nhật trạng thái
            order.setPaymentStatus(Order.PaymentStatus.refunded);
            order.setUpdatedAt(LocalDateTime.now());

            orderRepository.save(order);

            // 🔔 Push: thông báo hoàn tiền cho người mua (wallet notification đã gửi từ WalletServiceImpl)
            fcmService.sendOrderStatusChangedToBuyer(
                    order.getUser(),
                    order.getOrderNumber(),
                    order.getShop().getShopName(),
                    "refunded",
                    null
            );

            log.info("Order {} refunded successfully by seller {}", orderId, seller.getId());
        } catch (Exception e) {
            log.error("Failed to refund order {}: {}", orderId, e.getMessage());
            throw new RuntimeException("Không thể hoàn tiền: " + e.getMessage());
        }

        return mapToOrderResponseDTO(order);
    }

    @Override
    @Transactional
    public OrderResponseDTO cancelOrderBySeller(Integer orderId, User seller, String reason) {
        log.info("Seller {} is cancelling order {}", seller.getId(), orderId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn hàng"));

        // Kiểm tra quyền sở hữu
        if (!order.getShop().getSeller().getId().equals(seller.getId())) {
            throw new RuntimeException("Bạn không có quyền hủy đơn hàng này");
        }

        // Kiểm tra trạng thái đơn hàng
        if (order.getOrderStatus() == Order.OrderStatus.cancelled) {
            throw new RuntimeException("Đơn hàng đã bị hủy rồi");
        }

        if (order.getOrderStatus() == Order.OrderStatus.delivered) {
            throw new RuntimeException("Không thể hủy đơn hàng đã giao");
        }

        // Hoàn tiền nếu đơn hàng đã thanh toán
        if (order.getPaymentStatus() == Order.PaymentStatus.paid
                && order.getPaymentMethod() == Order.PaymentMethod.app_wallet) {
            try {
                log.info("Refunding wallet payment for cancelled order {}", orderId);
                walletService.refundOrder(order);
            } catch (Exception e) {
                log.error("Failed to refund order {}: {}", orderId, e.getMessage());
                throw new RuntimeException("Không thể hoàn tiền: " + e.getMessage());
            }
        }

        // Hủy đơn hàng
        order.setOrderStatus(Order.OrderStatus.cancelled);
        order.setCancelledAt(LocalDateTime.now());
        order.setCancelledBy(seller);
        order.setCancelledReason(reason != null ? reason : "Cancelled by seller");
        order.setUpdatedAt(LocalDateTime.now());

        orderRepository.save(order);

        // ===== HOÀN LẠI TỒN KHO KHI SELLER HỦY ĐƠN =====
        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        for (OrderItem item : orderItems) {
            Product product = item.getProduct();
            int restoredStock = product.getStockQuantity() + item.getQuantity();
            product.setStockQuantity(restoredStock);
            productRepository.save(product);
            log.info("Stock restored for product {}: +{} => {}", product.getId(), item.getQuantity(), restoredStock);
        }

        // 🔔 Push: thông báo đơn bị hủy cho người mua
        fcmService.sendOrderStatusChangedToBuyer(
                order.getUser(),
                order.getOrderNumber(),
                order.getShop().getShopName(),
                "cancelled",
                reason
        );

        log.info("Order {} cancelled successfully by seller {}", orderId, seller.getId());
        return mapToOrderResponseDTO(order);
    }

    @Override
    public Page<OrderResponseDTO> getOrdersWithRefundRequests(User seller, Pageable pageable) {
        log.info("Getting orders with refund requests for seller: {}", seller.getId());

        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0);

        Page<Order> orders = orderRepository.findByShop(shop, pageable);
        return orders
                .map(this::mapToOrderResponseDTO)
                .map(dto -> (dto.getHasRefundRequest() != null && dto.getHasRefundRequest()) ? dto : null);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrders(User seller, Pageable pageable) {
        log.info("Getting orders for seller: {} with pagination", seller.getId());

        // Tìm shop của seller
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0); // Lấy shop đầu tiên

        Page<Order> orders = orderRepository.findByShop(shop, pageable);
        return orders.map(this::mapToOrderResponseDTO);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrdersWithFilter(
            User seller,
            Order.OrderStatus status,
            LocalDateTime startDate,
            LocalDateTime endDate,
            Pageable pageable) {
        log.info("Getting filtered orders for seller: {}, status: {}, startDate: {}, endDate: {}",
                seller.getId(), status, startDate, endDate);

        // Tìm shop của seller
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0); // Lấy shop đầu tiên

        Page<Order> orders;

        // Lọc theo các điều kiện
        if (status != null && startDate != null && endDate != null) {
            // Lọc cả trạng thái và thời gian
            orders = orderRepository.findByShopAndOrderStatusAndCreatedAtBetween(shop, status, startDate, endDate, pageable);
        } else if (status != null) {
            // Chỉ lọc theo trạng thái
            orders = orderRepository.findByShopAndOrderStatus(shop, status, pageable);
        } else if (startDate != null && endDate != null) {
            // Chỉ lọc theo thời gian
            orders = orderRepository.findByShopAndCreatedAtBetween(shop, startDate, endDate, pageable);
        } else {
            // Không lọc gì, lấy tất cả
            orders = orderRepository.findByShop(shop, pageable);
        }

        return orders.map(this::mapToOrderResponseDTO);
    }

    @Override
    public Page<OrderResponseDTO> getShopOrdersWithFilterByStatusString(
            User seller, String status, LocalDateTime startDate, LocalDateTime endDate, Pageable pageable) {
        Order.OrderStatus orderStatus = null;
        if (status != null && !status.isEmpty()) {
            try {
                orderStatus = Order.OrderStatus.valueOf(status.toLowerCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Trạng thái đơn hàng không hợp lệ: " + status);
            }
        }
        return getShopOrdersWithFilter(seller, orderStatus, startDate, endDate, pageable);
    }

    @Override
    public List<RevenueStatisticsDTO> getRevenueStatistics(
            User seller,
            String periodType,
            LocalDateTime startDate,
            LocalDateTime endDate) {
        log.info("Getting revenue statistics for seller: {}, periodType: {}, startDate: {}, endDate: {}",
                seller.getId(), periodType, startDate, endDate);

        // Tìm shop của seller
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0); // Lấy shop đầu tiên

        // Lấy đơn hàng đã thanh toán trong khoảng thời gian
        List<Order> paidOrders = orderRepository.findPaidOrdersByShopAndDateRange(shop, startDate, endDate);

        // Nhóm theo period type
        Map<String, List<Order>> groupedOrders = new java.util.HashMap<>();

        for (Order order : paidOrders) {
            String period;
            if ("day".equalsIgnoreCase(periodType)) {
                period = order.getCreatedAt().toLocalDate().toString(); // "2024-01-15"
            } else if ("month".equalsIgnoreCase(periodType)) {
                period = order.getCreatedAt().getYear() + "-" +
                         String.format("%02d", order.getCreatedAt().getMonthValue()); // "2024-01"
            } else if ("year".equalsIgnoreCase(periodType)) {
                period = String.valueOf(order.getCreatedAt().getYear()); // "2024"
            } else {
                throw new RuntimeException("Invalid period type. Allowed: day, month, year");
            }

            groupedOrders.computeIfAbsent(period, k -> new java.util.ArrayList<>()).add(order);
        }

        // Tính toán thống kê cho từng period
        List<RevenueStatisticsDTO> statistics = new java.util.ArrayList<>();

        for (Map.Entry<String, List<Order>> entry : groupedOrders.entrySet()) {
            String period = entry.getKey();
            List<Order> orders = entry.getValue();

            long totalOrders = orders.size();
            BigDecimal totalRevenue = orders.stream()
                    .map(Order::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal platformCommission = orders.stream()
                    .map(o -> o.getPlatformCommissionAmount() != null ? o.getPlatformCommissionAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sellerRevenue = orders.stream()
                    .map(o -> o.getSellerReceivedAmount() != null ? o.getSellerReceivedAmount() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            statistics.add(new RevenueStatisticsDTO(period, totalOrders, totalRevenue, platformCommission, sellerRevenue));
        }

        // Sắp xếp theo period (tăng dần)
        statistics.sort((a, b) -> a.getPeriod().compareTo(b.getPeriod()));

        log.info("Revenue statistics calculated: {} periods", statistics.size());
        return statistics;
    }

    @Override
    public List<ProductSalesStatisticsDTO> getTopSellingProducts(
            User seller,
            LocalDateTime startDate,
            LocalDateTime endDate,
            int limit) {
        log.info("Getting top selling products for seller: {}, startDate: {}, endDate: {}, limit: {}",
                seller.getId(), startDate, endDate, limit);

        // Tìm shop của seller
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0); // Lấy shop đầu tiên

        // Lấy danh sách sản phẩm bán chạy
        List<Object[]> results = orderItemRepository.findTopSellingProductsByShopAndDateRange(shop, startDate, endDate);

        // Lấy hoa hồng platform để tính seller revenue
        BigDecimal commissionRate = new BigDecimal("12.00"); // 12%

        List<ProductSalesStatisticsDTO> statistics = new java.util.ArrayList<>();

        int count = 0;
        for (Object[] row : results) {
            if (count >= limit) break;

            Integer productId = (Integer) row[0];
            String productName = (String) row[1];
            String imageUrls = (String) row[2];
            Long totalQuantity = ((Number) row[3]).longValue();
            Long totalOrders = ((Number) row[4]).longValue();
            BigDecimal totalRevenue = (BigDecimal) row[5];

            // Tính seller revenue (sau khi trừ hoa hồng)
            BigDecimal sellerRevenue = totalRevenue
                    .multiply(BigDecimal.ONE.subtract(commissionRate.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);

            // Lấy ảnh đầu tiên
            String firstImage = "";
            if (imageUrls != null && !imageUrls.trim().isEmpty()) {
                String[] images = imageUrls.split(",");
                if (images.length > 0) {
                    firstImage = images[0].trim();
                }
            }

            ProductSalesStatisticsDTO dto = new ProductSalesStatisticsDTO(
                    productId, productName, firstImage, totalQuantity, totalOrders, totalRevenue, sellerRevenue);
            statistics.add(dto);
            count++;
        }

        log.info("Top selling products: {} products", statistics.size());
        return statistics;
    }

    private OrderResponseDTO mapToOrderResponseDTO(Order order) {
        OrderResponseDTO dto = new OrderResponseDTO();
        dto.setId(order.getId());
        dto.setOrderNumber(order.getOrderNumber());
        dto.setUserId(order.getUser().getId());
        dto.setUserName(order.getUser().getFullName());
        dto.setShopId(order.getShop().getId());
        dto.setShopName(order.getShop().getShopName());
        dto.setDeliveryAddress(mapToAddressResponseDTO(order.getDeliveryAddress()));
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

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        dto.setOrderItems(orderItems.stream().map(this::mapToOrderItemResponseDTO).collect(Collectors.toList()));

        return dto;
    }


    private OrderItemResponseDTO mapToOrderItemResponseDTO(OrderItem orderItem) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(orderItem.getId());
        dto.setProductId(orderItem.getProduct().getId());
        dto.setProductName(orderItem.getProduct().getName());

        // Map product image URLs
        if (orderItem.getProduct().getImageUrls() != null && !orderItem.getProduct().getImageUrls().trim().isEmpty()) {
            List<String> imageUrls = Arrays.stream(orderItem.getProduct().getImageUrls().split(","))
                    .map(String::trim)
                    .filter(url -> !url.isEmpty())
                    .collect(Collectors.toList());
            dto.setImageUrls(imageUrls.isEmpty() ? List.of() : imageUrls);
        } else {
            dto.setImageUrls(List.of());
        }

        dto.setQuantity(orderItem.getQuantity());
        dto.setUnitPrice(orderItem.getUnitPrice());
        dto.setTotalPrice(orderItem.getTotalPrice());
        try {
            Map<String, List<Integer>> variantMap = objectMapper.readValue(orderItem.getSelectedVariants(), Map.class);
            List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
            List<ProductVariant> variants = variantRepository.findAllById(variantIds);
            dto.setSelectedVariants(variants.stream().map(this::mapToVariantResponseDTO).collect(Collectors.toList()));
        } catch (Exception e) {
            log.error("Error deserializing variants for orderItem: {}: {}", orderItem.getId(), e.getMessage());
            dto.setSelectedVariants(List.of());
        }
        dto.setSpecialInstructions(orderItem.getSpecialInstructions());
        return dto;
    }


    private VariantResponseDTO mapToVariantResponseDTO(ProductVariant variant) {
        VariantResponseDTO dto = new VariantResponseDTO();
        dto.setId(variant.getId());
        dto.setVariantTypeId(variant.getVariantType().getId());
        dto.setVariantTypeName(variant.getVariantType().getName());
        dto.setVariantValue(variant.getVariantValue());
        dto.setPriceAdjustment(variant.getPriceAdjustment());
        dto.setIsActive(variant.getIsActive());
        return dto;
    }

    private Address createAddressFromDTO(AddressRequestDTO dto, User user) {
        Address address = new Address();
        address.setUser(user);
        address.setAddressLine(dto.getAddressLine());
        address.setWard(dto.getWard());
        address.setDistrict(dto.getDistrict());
        address.setCity(dto.getCity());
        address.setCountry(dto.getCountry() != null ? dto.getCountry() : "Vietnam");
        address.setPostalCode(dto.getPostalCode());
        address.setLatitude(dto.getLatitude());
        address.setLongitude(dto.getLongitude());
        address.setIsDefault(false); // ✅ Set default value
        address.setAddressType(Address.AddressType.other); // ✅ Set default value
        address.setCreatedAt(LocalDateTime.now());
        return address;
    }

    private AddressResponseDTO mapToAddressResponseDTO(Address address) {
        AddressResponseDTO dto = new AddressResponseDTO();
        dto.setId(address.getId());
        dto.setAddressLine(address.getAddressLine());
        dto.setWard(address.getWard());
        dto.setDistrict(address.getDistrict());
        dto.setCity(address.getCity());
        dto.setCountry(address.getCountry());
        dto.setPostalCode(address.getPostalCode());
        dto.setLatitude(address.getLatitude());
        dto.setLongitude(address.getLongitude());

        StringBuilder fullAddress = new StringBuilder();
        if (address.getAddressLine() != null) fullAddress.append(address.getAddressLine());
        if (address.getWard() != null) fullAddress.append(", ").append(address.getWard());
        if (address.getDistrict() != null) fullAddress.append(", ").append(address.getDistrict());
        if (address.getCity() != null) fullAddress.append(", ").append(address.getCity());
        dto.setFullAddress(fullAddress.toString());

        return dto;
    }

    @Override
    public DashboardStatisticsDTO getDashboardStatistics(User seller, LocalDateTime startDate, LocalDateTime endDate) {
        log.info("Getting dashboard statistics for seller: {}, startDate: {}, endDate: {}",
                seller.getId(), startDate, endDate);

        // Tìm shop của seller
        List<Shop> shops = shopRepository.findBySeller(seller);
        if (shops.isEmpty()) {
            throw new RuntimeException("Bạn chưa có shop nào");
        }
        Shop shop = shops.get(0); // Lấy shop đầu tiên

        DashboardStatisticsDTO dashboard = new DashboardStatisticsDTO();

        // Đếm đơn hàng theo trạng thái
        Long totalOrders = orderRepository.countByShop(shop);
        Long pendingOrders = orderRepository.countByShopAndOrderStatus(shop, Order.OrderStatus.pending);
        Long completedOrders = orderRepository.countByShopAndOrderStatus(shop, Order.OrderStatus.delivered);
        Long cancelledOrders = orderRepository.countByShopAndOrderStatus(shop, Order.OrderStatus.cancelled);

        dashboard.setTotalOrders(totalOrders);
        dashboard.setPendingOrders(pendingOrders);
        dashboard.setCompletedOrders(completedOrders);
        dashboard.setCancelledOrders(cancelledOrders);

        // Tính doanh thu trong khoảng thời gian
        List<Order> paidOrders = orderRepository.findPaidOrdersByShopAndDateRange(shop, startDate, endDate);

        BigDecimal totalRevenue = paidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal platformCommission = paidOrders.stream()
                .map(o -> o.getPlatformCommissionAmount() != null ? o.getPlatformCommissionAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sellerRevenue = paidOrders.stream()
                .map(o -> o.getSellerReceivedAmount() != null ? o.getSellerReceivedAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dashboard.setTotalRevenue(totalRevenue);
        dashboard.setPlatformCommission(platformCommission);
        dashboard.setSellerRevenue(sellerRevenue);

        // Lấy top 10 sản phẩm bán chạy
        List<ProductSalesStatisticsDTO> topProducts = getTopSellingProducts(seller, startDate, endDate, 10);
        dashboard.setTopSellingProducts(topProducts);

        log.info("Dashboard statistics calculated successfully");
        return dashboard;
    }
}
