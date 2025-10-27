package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.OrderDTO.CreateOrderRequestDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderItemResponseDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.AddressResponseDTO;
import com.example.FoodTourApp.entity.*;
import com.example.FoodTourApp.repository.*;
import com.example.FoodTourApp.service.OrderService;
import com.example.FoodTourApp.service.WalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartItemRepository cartItemRepository;
    private final AddressRepository addressRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final WalletService walletService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public OrderResponseDTO createOrder(CreateOrderRequestDTO request, User user) {
        log.info("Creating order for user: {}, cartItemIds: {}, paymentMethod: {}",
                user.getId(), request.getCartItemIds(), request.getPaymentMethod());

        // Kiểm tra cartItemIds
        List<CartItem> cartItems = cartItemRepository.findAllById(request.getCartItemIds()).stream()
                .filter(cartItem -> cartItem.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());

        if (cartItems.isEmpty()) {
            throw new RuntimeException("No valid cart items selected");
        }

        if (cartItems.size() != request.getCartItemIds().size()) {
            throw new RuntimeException("One or more cart items are invalid or not owned by user");
        }

        // Kiểm tra tất cả CartItem thuộc cùng shop
        Integer shopId = cartItems.get(0).getProduct().getShop().getId();
        boolean allSameShop = cartItems.stream()
                .allMatch(cartItem -> cartItem.getProduct().getShop().getId().equals(shopId));
        if (!allSameShop) {
            throw new RuntimeException("Selected cart items must belong to the same shop");
        }

        // Kiểm tra payment method
        try {
            Order.PaymentMethod paymentMethod = Order.PaymentMethod.valueOf(request.getPaymentMethod());
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid payment method");
        }

        // Tạo và lưu địa chỉ giao hàng
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
        order.setPaymentMethod(Order.PaymentMethod.valueOf(request.getPaymentMethod()));
        order.setNotes(request.getNotes());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        // Tính toán giá
        double subtotal = 0.0;
        for (CartItem cartItem : cartItems) {
            if (!cartItem.getProduct().getIsAvailable()) {
                throw new RuntimeException("Product " + cartItem.getProduct().getName() + " is not available");
            }
            if (cartItem.getQuantity() < cartItem.getProduct().getMinOrderQuantity() ||
                    cartItem.getQuantity() > cartItem.getProduct().getMaxOrderQuantity()) {
                throw new RuntimeException("Invalid quantity for product " + cartItem.getProduct().getName());
            }

            double itemTotal = cartItem.getProduct().getPrice() * cartItem.getQuantity();
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
                    itemTotal += (variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : 0) * cartItem.getQuantity();
                }
            } catch (Exception e) {
                log.error("Error deserializing variants for cartItem: {}: {}", cartItem.getId(), e.getMessage());
                throw new RuntimeException("Error processing variants for product " + cartItem.getProduct().getName());
            }
            subtotal += itemTotal;
        }

        order.setSubtotal(subtotal);
        order.setDeliveryFee(15000.0);
        order.setTaxAmount(subtotal * 0.1);
        order.setDiscountAmount(0.0);
        order.setTotalAmount(subtotal + order.getDeliveryFee() + order.getTaxAmount() - order.getDiscountAmount());

        order = orderRepository.save(order);

        // Tạo OrderItem từ CartItem
        for (CartItem cartItem : cartItems) {
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setProduct(cartItem.getProduct());
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setUnitPrice(cartItem.getProduct().getPrice());
            double itemTotal = cartItem.getProduct().getPrice() * cartItem.getQuantity();
            try {
                Map<String, List<Integer>> variantMap = objectMapper.readValue(cartItem.getSelectedVariants(), Map.class);
                List<Integer> variantIds = variantMap.getOrDefault("variantIds", List.of());
                List<ProductVariant> variants = variantRepository.findAllById(variantIds);
                for (ProductVariant variant : variants) {
                    itemTotal += (variant.getPriceAdjustment() != null ? variant.getPriceAdjustment() : 0) * cartItem.getQuantity();
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

        log.info("Order created successfully: orderId: {}, paymentStatus: {}",
                order.getId(), order.getPaymentStatus());
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
            } catch (Exception e) {
                log.error("Wallet payment failed for order {}: {}", order.getId(), e.getMessage());
                throw new RuntimeException("Thanh toán thất bại: " + e.getMessage());
            }
        } else if (order.getPaymentMethod() == Order.PaymentMethod.ship_cod) {
            // COD không cần thanh toán ngay, chỉ cập nhật trạng thái
            log.info("Order {} is COD, no immediate payment required", order.getId());
            throw new RuntimeException("Đơn hàng COD sẽ thanh toán khi nhận hàng");
        } else {
            throw new RuntimeException("Phương thức thanh toán không được hỗ trợ");
        }

        return mapToOrderResponseDTO(order);
    }

    @Override
    public List<OrderResponseDTO> getUserOrders(User user) {
        log.info("Getting orders for user: {}", user.getId());

        List<Order> orders = orderRepository.findByUser(user);
        return orders.stream().map(this::mapToOrderResponseDTO).collect(Collectors.toList());
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
        log.info("Order deleted (soft delete) successfully: {}", orderId);
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

        List<OrderItem> orderItems = orderItemRepository.findByOrder(order);
        dto.setOrderItems(orderItems.stream().map(this::mapToOrderItemResponseDTO).collect(Collectors.toList()));

        return dto;
    }


    private OrderItemResponseDTO mapToOrderItemResponseDTO(OrderItem orderItem) {
        OrderItemResponseDTO dto = new OrderItemResponseDTO();
        dto.setId(orderItem.getId());
        dto.setProductId(orderItem.getProduct().getId());
        dto.setProductName(orderItem.getProduct().getName());
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
        address.setIsDefault(false);
        address.setAddressType(Address.AddressType.other);
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
}
