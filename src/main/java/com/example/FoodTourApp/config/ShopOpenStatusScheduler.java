package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ProductSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tự động cập nhật trạng thái mở/đóng cửa của shop trong Elasticsearch mỗi 60 giây.
 *
 * <p>Đây là cách production-correct để xử lý open/close status:
 * <ul>
 *   <li>KHÔNG cần run script tay — chạy tự động bên trong Spring app</li>
 *   <li>Chỉ update ES cho các shop có trạng thái thay đổi → hiệu quả</li>
 *   <li>shop_is_open được filter tại ES level → pagination chính xác</li>
 *   <li>Độ trễ tối đa 60 giây khi shop đổi trạng thái — chấp nhận được</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShopOpenStatusScheduler {

    private final ShopRepository shopRepository;
    private final ProductSearchService productSearchService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Cache trạng thái trước đó — chỉ update ES khi trạng thái thay đổi
    private final Map<Integer, Boolean> previousOpenStatus = new ConcurrentHashMap<>();

    /**
     * Chạy ngay khi app khởi động (initialDelay=0), sau đó mỗi 60 giây.
     * Kiểm tra tất cả shop verified+active, tính isOpen theo opening_hours,
     * và chỉ gọi ES update_by_query cho shop nào vừa đổi trạng thái.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 0)
    public void updateShopOpenStatuses() {
        if (!productSearchService.isElasticsearchAvailable()) {
            log.debug("[ShopOpenScheduler] ES not available, skip");
            return;
        }

        try {
            List<Shop> shops = shopRepository.findByIsVerifiedTrueAndIsActiveTrue();
            int updated = 0;

            for (Shop shop : shops) {
                boolean isOpen = isShopCurrentlyOpen(shop.getOpeningHours());
                Boolean previous = previousOpenStatus.get(shop.getId());

                // Chỉ update ES nếu trạng thái thay đổi (hoặc lần đầu chạy)
                if (previous == null || previous != isOpen) {
                    productSearchService.updateShopOpenStatusInEs(shop.getId(), isOpen);
                    previousOpenStatus.put(shop.getId(), isOpen);
                    updated++;
                    log.info("[ShopOpenScheduler] Shop {} ({}) → {}",
                            shop.getId(), shop.getShopName(), isOpen ? "OPEN" : "CLOSED");
                }
            }

            if (updated > 0) {
                log.info("[ShopOpenScheduler] Updated {}/{} shops' open/close status in ES",
                        updated, shops.size());
            }
        } catch (Exception e) {
            log.error("[ShopOpenScheduler] Failed to update shop open statuses", e);
        }
    }

    /**
     * Tính trạng thái mở/đóng cửa của shop tại thời điểm hiện tại.
     * Format: {"monday": {"open": "08:00", "close": "22:00"}, ...}
     * Nếu opening_hours null/rỗng → coi như mở 24/7.
     */
    private boolean isShopCurrentlyOpen(String openingHoursJson) {
        if (openingHoursJson == null || openingHoursJson.isBlank()) return true;
        try {
            Map<String, Map<String, String>> hoursMap = OBJECT_MAPPER.readValue(
                    openingHoursJson, new TypeReference<Map<String, Map<String, String>>>() {});

            LocalDateTime now = LocalDateTime.now();
            String dayKey = now.getDayOfWeek().name().toLowerCase(); // "monday", "tuesday", ...
            Map<String, String> todayHours = hoursMap.get(dayKey);
            if (todayHours == null) return false;

            String openStr = todayHours.get("open");
            String closeStr = todayHours.get("close");
            if (openStr == null || closeStr == null) return false;

            LocalTime currentTime = now.toLocalTime();
            LocalTime openTime = LocalTime.parse(openStr);
            LocalTime closeTime = LocalTime.parse(closeStr);

            if (closeTime.isBefore(openTime)) {
                // Qua đêm (e.g. 22:00–02:00)
                return currentTime.isAfter(openTime) || currentTime.isBefore(closeTime);
            }
            return !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime);
        } catch (Exception e) {
            log.warn("[ShopOpenScheduler] Failed to parse opening_hours: {}", e.getMessage());
            return true;
        }
    }
}
