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

    /**
     * Lấy danh sách đơn hàng của shop
     * GET /api/seller/orders
     */
    @GetMapping
    public ResponseEntity<?> getShopOrders(
            @AuthenticationPrincipal User seller,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Seller ID {} is getting shop orders with pagination", seller.getId());
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<OrderResponseDTO> orders = orderService.getShopOrders(seller, pageable);
            PageResponse<OrderResponseDTO> pageResponse = PageResponse.of(orders);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            logger.error("Error getting shop orders for seller ID {}: {}", seller.getId(), e.getMessage(), e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * Lọc đơn hàng theo trạng thái và thời gian
     * GET /api/seller/orders/filter
     * Params: status (optional), startDate (optional), endDate (optional)
     */
    @GetMapping("/filter")
    public ResponseEntity<?> getShopOrdersWithFilter(
            @AuthenticationPrincipal User seller,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir) {
        logger.info("Seller ID {} is filtering shop orders: status={}, startDate={}, endDate={}",
                seller.getId(), status, startDate, endDate);
        try {
            Sort sort = sortDir.equalsIgnoreCase("ASC") ?
                    Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
            Pageable pageable = PageRequest.of(page, size, sort);

            Order.OrderStatus orderStatus = null;
            if (status != null && !status.isEmpty()) {
                try {
                    orderStatus = Order.OrderStatus.valueOf(status.toLowerCase());
                } catch (IllegalArgumentException e) {
                    throw new RuntimeException("Invalid order status: " + status);
                }
            }

            Page<OrderResponseDTO> orders = orderService.getShopOrdersWithFilter(
                    seller, orderStatus, startDate, endDate, pageable);
            PageResponse<OrderResponseDTO> pageResponse = PageResponse.of(orders);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("data", pageResponse);
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
     * Thống kê doanh thu theo ngày/tháng/năm
     * GET /api/seller/orders/statistics/revenue
     * Params: periodType (day/month/year), startDate, endDate
     */
    @GetMapping("/statistics/revenue")
    public ResponseEntity<?> getRevenueStatistics(
            @AuthenticationPrincipal User seller,
            @RequestParam String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        logger.info("Seller ID {} is getting revenue statistics: periodType={}, startDate={}, endDate={}",
                seller.getId(), periodType, startDate, endDate);
        try {
            List<RevenueStatisticsDTO> statistics = orderService.getRevenueStatistics(
                    seller, periodType, startDate, endDate);

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
     * GET /api/seller/orders/statistics/top-products
     * Params: startDate, endDate, limit (default: 10)
     */
    @GetMapping("/statistics/top-products")
    public ResponseEntity<?> getTopSellingProducts(
            @AuthenticationPrincipal User seller,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "10") int limit) {
        logger.info("Seller ID {} is getting top selling products: startDate={}, endDate={}, limit={}",
                seller.getId(), startDate, endDate, limit);
        try {
            List<ProductSalesStatisticsDTO> statistics = orderService.getTopSellingProducts(
                    seller, startDate, endDate, limit);

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
     * Dashboard tổng quan cho seller
     * GET /api/seller/orders/statistics/dashboard
     * Params: startDate, endDate
     */
    @GetMapping("/statistics/dashboard")
    public ResponseEntity<?> getDashboardStatistics(
            @AuthenticationPrincipal User seller,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        logger.info("Seller ID {} is getting dashboard statistics: startDate={}, endDate={}",
                seller.getId(), startDate, endDate);
        try {
            DashboardStatisticsDTO dashboard = orderService.getDashboardStatistics(seller, startDate, endDate);

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
}
