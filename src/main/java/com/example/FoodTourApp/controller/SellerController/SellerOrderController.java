package com.example.FoodTourApp.controller.SellerController;

import com.example.FoodTourApp.DTO.OrderDTO.DashboardStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.OrderResponseDTO;
import com.example.FoodTourApp.DTO.OrderDTO.ProductSalesStatisticsDTO;
import com.example.FoodTourApp.DTO.OrderDTO.RevenueStatisticsDTO;
import com.example.FoodTourApp.DTO.PageResponse;
import com.example.FoodTourApp.entity.Order;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/seller/orders")
@RequiredArgsConstructor
public class SellerOrderController {

    private final OrderService orderService;
    private static final Logger logger = LoggerFactory.getLogger(SellerOrderController.class);

    // ══════════════════════════════════════════════════════════════════════════
    // Tổng hợp TẤT CẢ shops (không truyền shopId)
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy / lọc đơn hàng tổng hợp tất cả shops.
     * GET /api/seller/orders
     * GET /api/seller/orders?status=PENDING&startDate=...&endDate=...
     * Nếu không truyền filter thì trả về tất cả.
     */
    @GetMapping
    public ResponseEntity<?> getShopOrders(
            @AuthenticationPrincipal User seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return getShopOrdersWithFilterInternal(seller, null, status, startDate, endDate, page, size, sortBy, sortDir);
    }

    // ══════════════════════════════════════════════════════════════════════════
    // Theo shop cụ thể /{shopId}/...
    // ══════════════════════════════════════════════════════════════════════════

    /**
     * Lấy / lọc đơn hàng theo shop cụ thể.
     * GET /api/seller/orders/{shopId}
     * GET /api/seller/orders/{shopId}?status=PENDING&startDate=...&endDate=...
     * Nếu không truyền filter thì trả về tất cả đơn của shop đó.
     */
    @GetMapping("/{shopId}")
    public ResponseEntity<?> getShopOrdersByShopId(
            @AuthenticationPrincipal User seller,
            @PathVariable Integer shopId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return getShopOrdersWithFilterInternal(seller, shopId, status, startDate, endDate, page, size, sortBy, sortDir);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private ResponseEntity<?> getShopOrdersWithFilterInternal(User seller, Integer shopId,
            String status, LocalDateTime startDate, LocalDateTime endDate,
            int page, int size, String sortBy, String sortDir) {
        logger.info("Seller ID {} filtering orders: shopId={}, status={}, startDate={}, endDate={}",
                seller.getId(), shopId, status, startDate, endDate);
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<OrderResponseDTO> orders = orderService.getShopOrdersWithFilterByStatusString(
                    seller, shopId, status, startDate, endDate, pageable);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", PageResponse.of(orders));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error filtering shop orders for seller ID {}: {}", seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Seller xác nhận đã nhận tiền COD từ shipper
     * POST /api/seller/orders/{orderId}/confirm-cod
     */
    @PostMapping("/{orderId}/confirm-cod")
    public ResponseEntity<?> confirmCODPayment(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal User seller) {
        logger.info("Seller ID {} is confirming COD payment for order {}", seller.getId(), orderId);
        try {
            OrderResponseDTO order = orderService.confirmCODPayment(orderId, seller);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Xác nhận đã nhận tiền COD thành công. Đơn hàng hoàn tất.");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error confirming COD payment for order {} by seller ID {}: {}",
                    orderId, seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Seller xác nhận đơn hàng (chuyển từ pending sang confirmed)
     * POST /api/seller/orders/{orderId}/confirm
     */
    @PostMapping("/{orderId}/confirm")
    public ResponseEntity<?> confirmOrder(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal User seller) {
        logger.info("Seller ID {} is confirming order {}", seller.getId(), orderId);
        try {
            OrderResponseDTO order = orderService.confirmOrder(orderId, seller);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Xác nhận đơn hàng thành công");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error confirming order {} by seller ID {}: {}",
                    orderId, seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Seller đánh dấu đơn hàng đã giao (chuyển sang delivered)
     * POST /api/seller/orders/{orderId}/deliver
     */
    @PostMapping("/{orderId}/deliver")
    public ResponseEntity<?> markAsDelivered(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal User seller) {
        logger.info("Seller ID {} is marking order {} as delivered", seller.getId(), orderId);
        try {
            OrderResponseDTO order = orderService.markAsDelivered(orderId, seller);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Đánh dấu đã giao hàng thành công");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error marking order {} as delivered by seller ID {}: {}",
                    orderId, seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Seller hoàn tiền cho đơn hàng
     * POST /api/seller/orders/{orderId}/refund
     */
    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> refundOrder(
            @PathVariable Integer orderId,
            @AuthenticationPrincipal User seller) {
        logger.info("Seller ID {} is processing refund for order {}", seller.getId(), orderId);
        try {
            OrderResponseDTO order = orderService.refundOrder(orderId, seller);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Hoàn tiền thành công");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error processing refund for order {} by seller ID {}: {}",
                    orderId, seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Seller hủy đơn hàng
     * POST /api/seller/orders/{orderId}/cancel
     */
    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable Integer orderId,
            @RequestBody(required = false) Map<String, String> requestBody,
            @AuthenticationPrincipal User seller) {
        logger.info("Seller ID {} is cancelling order {}", seller.getId(), orderId);
        try {
            String reason = requestBody != null ? requestBody.get("reason") : null;
            OrderResponseDTO order = orderService.cancelOrderBySeller(orderId, seller, reason);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Hủy đơn hàng thành công");
            result.put("data", order);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error cancelling order {} by seller ID {}: {}",
                    orderId, seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Thống kê doanh thu theo ngày/tháng/năm
     * GET /api/seller/orders/statistics/revenue           (tất cả shops)
     * GET /api/seller/orders/{shopId}/statistics/revenue  (shop cụ thể)
     */
    @GetMapping("/statistics/revenue")
    public ResponseEntity<?> getRevenueStatistics(
            @AuthenticationPrincipal User seller,
            @RequestParam String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return getRevenueStatisticsInternal(seller, null, periodType, startDate, endDate);
    }

    @GetMapping("/{shopId}/statistics/revenue")
    public ResponseEntity<?> getRevenueStatisticsByShopId(
            @AuthenticationPrincipal User seller,
            @PathVariable Integer shopId,
            @RequestParam String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return getRevenueStatisticsInternal(seller, shopId, periodType, startDate, endDate);
    }

    private ResponseEntity<?> getRevenueStatisticsInternal(User seller, Integer shopId,
            String periodType, LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("Seller ID {} getting revenue stats: shopId={}, periodType={}", seller.getId(), shopId, periodType);
        try {
            List<RevenueStatisticsDTO> statistics = orderService.getRevenueStatistics(
                    seller, shopId, periodType, startDate, endDate);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", statistics);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting revenue statistics for seller ID {}: {}", seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Thống kê sản phẩm bán chạy
     * GET /api/seller/orders/statistics/top-products           (tất cả shops)
     * GET /api/seller/orders/{shopId}/statistics/top-products  (shop cụ thể)
     */
    @GetMapping("/statistics/top-products")
    public ResponseEntity<?> getTopSellingProducts(
            @AuthenticationPrincipal User seller,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return getTopSellingProductsInternal(seller, null, startDate, endDate, limit);
    }

    @GetMapping("/{shopId}/statistics/top-products")
    public ResponseEntity<?> getTopSellingProductsByShopId(
            @AuthenticationPrincipal User seller,
            @PathVariable Integer shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {
        return getTopSellingProductsInternal(seller, shopId, startDate, endDate, limit);
    }

    private ResponseEntity<?> getTopSellingProductsInternal(User seller, Integer shopId,
            LocalDateTime startDate, LocalDateTime endDate, int limit) {
        logger.info("Seller ID {} getting top products: shopId={}, limit={}", seller.getId(), shopId, limit);
        try {
            List<ProductSalesStatisticsDTO> statistics = orderService.getTopSellingProducts(
                    seller, shopId, startDate, endDate, limit);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", statistics);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting top selling products for seller ID {}: {}", seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Dashboard tổng quan
     * GET /api/seller/orders/statistics/dashboard           (tất cả shops)
     * GET /api/seller/orders/{shopId}/statistics/dashboard  (shop cụ thể)
     */
    @GetMapping("/statistics/dashboard")
    public ResponseEntity<?> getDashboardStatistics(
            @AuthenticationPrincipal User seller,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return getDashboardStatisticsInternal(seller, null, startDate, endDate);
    }

    @GetMapping("/{shopId}/statistics/dashboard")
    public ResponseEntity<?> getDashboardStatisticsByShopId(
            @AuthenticationPrincipal User seller,
            @PathVariable Integer shopId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        return getDashboardStatisticsInternal(seller, shopId, startDate, endDate);
    }

    private ResponseEntity<?> getDashboardStatisticsInternal(User seller, Integer shopId,
            LocalDateTime startDate, LocalDateTime endDate) {
        logger.info("Seller ID {} getting dashboard: shopId={}", seller.getId(), shopId);
        try {
            DashboardStatisticsDTO dashboard = orderService.getDashboardStatistics(seller, shopId, startDate, endDate);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", dashboard);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting dashboard statistics for seller ID {}: {}", seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Đơn hàng có yêu cầu refund
     * GET /api/seller/orders/refund-requests           (tất cả shops)
     * GET /api/seller/orders/{shopId}/refund-requests  (shop cụ thể)
     */
    @GetMapping("/refund-requests")
    public ResponseEntity<?> getOrdersWithRefundRequests(
            @AuthenticationPrincipal User seller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return getOrdersWithRefundRequestsInternal(seller, null, page, size, sortBy, sortDir);
    }

    @GetMapping("/{shopId}/refund-requests")
    public ResponseEntity<?> getOrdersWithRefundRequestsByShopId(
            @AuthenticationPrincipal User seller,
            @PathVariable Integer shopId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        return getOrdersWithRefundRequestsInternal(seller, shopId, page, size, sortBy, sortDir);
    }

    private ResponseEntity<?> getOrdersWithRefundRequestsInternal(User seller, Integer shopId,
            int page, int size, String sortBy, String sortDir) {
        logger.info("Seller ID {} getting refund requests, shopId={}", seller.getId(), shopId);
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<OrderResponseDTO> ordersWithRefund = orderService.getOrdersWithRefundRequests(seller, shopId, pageable);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Danh sách đơn hàng có yêu cầu hoàn tiền");
            result.put("data", PageResponse.of(ordersWithRefund));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting orders with refund requests for seller ID {}: {}",
                    seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}