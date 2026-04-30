package com.example.FoodTourApp.config;

import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ProductSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * Event-driven shop open/close status scheduler.
 *
 * <p>Thay vì polling mỗi 60 giây, scheduler này:
 * <ul>
 *   <li>Tính chính xác thời điểm shop sẽ mở/đóng cửa tiếp theo</li>
 *   <li>Schedule một task chạy đúng lúc đó (zero latency)</li>
 *   <li>Sau khi task fire, tự schedule lại cho lần chuyển trạng thái tiếp theo</li>
 *   <li>Khi shop thay đổi opening_hours, gọi rescheduleShop() để cập nhật</li>
 * </ul>
 *
 * <p>Scale tốt dù có hàng nghìn shop — không tốn CPU/DB khi không có gì thay đổi.
 */
@Component
@Slf4j
public class ShopOpenStatusScheduler {

    private final ShopRepository shopRepository;
    private final ProductSearchService productSearchService;
    private final TaskScheduler taskScheduler;

    public ShopOpenStatusScheduler(
            ShopRepository shopRepository,
            ProductSearchService productSearchService,
            @Qualifier("shopTransitionTaskScheduler") TaskScheduler taskScheduler
    ) {
        this.shopRepository = shopRepository;
        this.productSearchService = productSearchService;
        this.taskScheduler = taskScheduler;
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // Map shopId → ScheduledFuture để có thể cancel khi cần reschedule
    private final Map<Integer, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    /**
     * Khi app khởi động: đồng bộ trạng thái hiện tại cho tất cả shop,
     * sau đó schedule transition tiếp theo cho từng shop.
     */
    @PostConstruct
    public void initialize() {
        if (!productSearchService.isElasticsearchAvailable()) {
            log.warn("[ShopOpenScheduler] ES not available at startup, skipping initialization");
            return;
        }

        List<Shop> shops = shopRepository.findByIsVerifiedTrueAndIsActiveTrue();
        int scheduled = 0;

        for (Shop shop : shops) {
            boolean isOpen = isShopCurrentlyOpen(shop.getOpeningHours());
            productSearchService.updateShopOpenStatusInEs(shop.getId(), isOpen);
            log.info("[ShopOpenScheduler] Init: Shop {} ({}) → {}", shop.getId(), shop.getShopName(), isOpen ? "OPEN" : "CLOSED");

            if (scheduleNextTransition(shop)) scheduled++;
        }

        log.info("[ShopOpenScheduler] Initialized {}/{} shops. {} transitions scheduled.",
                shops.size(), shops.size(), scheduled);
    }

    /**
     * Được gọi từ ShopEntityListener khi shop cập nhật opening_hours, hoặc thay đổi
     * trạng thái verified/active. Cancel task cũ, set trạng thái hiện tại, schedule lại.
     */
    public void rescheduleShop(Shop shop) {
        // Cancel task cũ nếu có
        ScheduledFuture<?> existing = scheduledTasks.remove(shop.getId());
        if (existing != null) existing.cancel(false);

        // Nếu shop không còn hoạt động, không cần schedule
        if (!Boolean.TRUE.equals(shop.getIsVerified()) || !Boolean.TRUE.equals(shop.getIsActive())) {
            log.debug("[ShopOpenScheduler] Shop {} is not active/verified, skipping schedule", shop.getId());
            return;
        }

        if (!productSearchService.isElasticsearchAvailable()) return;

        boolean isOpen = isShopCurrentlyOpen(shop.getOpeningHours());
        productSearchService.updateShopOpenStatusInEs(shop.getId(), isOpen);
        scheduleNextTransition(shop);
        log.info("[ShopOpenScheduler] Rescheduled shop {} ({}) → current: {}",
                shop.getId(), shop.getShopName(), isOpen ? "OPEN" : "CLOSED");
    }

    /**
     * Tính thời điểm chuyển trạng thái tiếp theo và schedule task cho shop đó.
     *
     * @return true nếu có transition được schedule, false nếu shop mở 24/7 hoặc không có hours
     */
    private boolean scheduleNextTransition(Shop shop) {
        Instant nextTransition = calculateNextTransition(shop.getOpeningHours());
        if (nextTransition == null) return false; // 24/7 hoặc không có opening hours

        ScheduledFuture<?> future = taskScheduler.schedule(() -> {
            try {
                if (!productSearchService.isElasticsearchAvailable()) return;

                // Lấy fresh data từ DB để tránh dùng stale opening_hours
                Shop freshShop = shopRepository.findById(shop.getId()).orElse(null);
                if (freshShop == null || !Boolean.TRUE.equals(freshShop.getIsVerified())
                        || !Boolean.TRUE.equals(freshShop.getIsActive())) {
                    scheduledTasks.remove(shop.getId());
                    return;
                }

                boolean isOpen = isShopCurrentlyOpen(freshShop.getOpeningHours());
                productSearchService.updateShopOpenStatusInEs(freshShop.getId(), isOpen);
                log.info("[ShopOpenScheduler] Transition: Shop {} ({}) → {}",
                        freshShop.getId(), freshShop.getShopName(), isOpen ? "OPEN" : "CLOSED");

                // Schedule lần chuyển trạng thái tiếp theo
                scheduledTasks.remove(freshShop.getId());
                scheduleNextTransition(freshShop);

            } catch (Exception e) {
                log.error("[ShopOpenScheduler] Error processing transition for shop {}: {}",
                        shop.getId(), e.getMessage());
                scheduledTasks.remove(shop.getId());
                scheduleNextTransition(shop); // Thử schedule lại dù có lỗi
            }
        }, nextTransition);

        scheduledTasks.put(shop.getId(), future);
        log.debug("[ShopOpenScheduler] Shop {} next transition at {}", shop.getId(), nextTransition);
        return true;
    }

    /**
     * Tính thời điểm chuyển trạng thái tiếp theo (OPEN→CLOSED hoặc CLOSED→OPEN).
     * Tìm kiếm trong vòng 8 ngày tới.
     *
     * @return Instant của transition tiếp theo, hoặc null nếu shop mở 24/7 / không có hours
     */
    private Instant calculateNextTransition(String openingHoursJson) {
        if (openingHoursJson == null || openingHoursJson.isBlank()) return null;

        try {
            Map<String, Map<String, String>> hoursMap = OBJECT_MAPPER.readValue(
                    openingHoursJson, new TypeReference<Map<String, Map<String, String>>>() {});

            LocalDateTime now = LocalDateTime.now();

            for (int daysAhead = 0; daysAhead <= 7; daysAhead++) {
                LocalDate checkDate = now.toLocalDate().plusDays(daysAhead);
                String dayKey = checkDate.getDayOfWeek().name().toLowerCase();
                Map<String, String> dayHours = hoursMap.get(dayKey);

                if (dayHours == null || dayHours.get("open") == null || dayHours.get("close") == null) {
                    continue;
                }

                LocalTime openTime = LocalTime.parse(dayHours.get("open"));
                LocalTime closeTime = LocalTime.parse(dayHours.get("close"));

                LocalDateTime openDt = checkDate.atTime(openTime);
                LocalDateTime closeDt = closeTime.isBefore(openTime)
                        ? checkDate.plusDays(1).atTime(closeTime)  // Overnight (e.g. 22:00–02:00)
                        : checkDate.atTime(closeTime);

                // Trả về event đầu tiên trong tương lai
                if (openDt.isAfter(now)) {
                    return openDt.atZone(ZoneId.systemDefault()).toInstant();
                }
                if (closeDt.isAfter(now)) {
                    return closeDt.atZone(ZoneId.systemDefault()).toInstant();
                }
            }

            return null; // Không tìm thấy transition trong 7 ngày tới
        } catch (Exception e) {
            log.warn("[ShopOpenScheduler] Failed to calculate next transition: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tính trạng thái mở/đóng cửa tại thời điểm hiện tại.
     * Format: {"monday": {"open": "08:00", "close": "22:00"}, ...}
     * null/rỗng → mở 24/7.
     */
    public boolean isShopCurrentlyOpen(String openingHoursJson) {
        if (openingHoursJson == null || openingHoursJson.isBlank()) return true;
        try {
            Map<String, Map<String, String>> hoursMap = OBJECT_MAPPER.readValue(
                    openingHoursJson, new TypeReference<Map<String, Map<String, String>>>() {});

            LocalDateTime now = LocalDateTime.now();
            String dayKey = now.getDayOfWeek().name().toLowerCase();
            Map<String, String> todayHours = hoursMap.get(dayKey);
            if (todayHours == null) return false;

            String openStr = todayHours.get("open");
            String closeStr = todayHours.get("close");
            if (openStr == null || closeStr == null) return false;

            LocalTime currentTime = now.toLocalTime();
            LocalTime openTime = LocalTime.parse(openStr);
            LocalTime closeTime = LocalTime.parse(closeStr);

            if (closeTime.isBefore(openTime)) {
                // Overnight (e.g. 22:00–02:00)
                return currentTime.isAfter(openTime) || currentTime.isBefore(closeTime);
            }
            return !currentTime.isBefore(openTime) && currentTime.isBefore(closeTime);
        } catch (Exception e) {
            log.warn("[ShopOpenScheduler] Failed to parse opening_hours: {}", e.getMessage());
            return true;
        }
    }
}
