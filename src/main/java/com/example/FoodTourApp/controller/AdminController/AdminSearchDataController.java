package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.service.SearchAnalyticsEsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin API để export search analytics data cho Google Colab train PhoBERT.
 *
 * CÁCH SỬ DỤNG:
 * 1. Login admin → lấy JWT token
 * 2. GET /api/admin/search/analytics/export?days=90
 *    → Download search_analytics.csv
 * 3. Upload CSV lên Colab → chạy Cell 6 trở đi
 *
 * SearchAnalytics không còn lưu trong MySQL — toàn bộ nằm trong ES index "search_analytics".
 * Endpoint này dùng ES scroll API để fetch bulk data (không bị giới hạn 10000 docs).
 */
@RestController
@RequestMapping("/api/admin/search")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminSearchDataController {

    private final SearchAnalyticsEsService searchAnalyticsEsService;

    /**
     * GET /api/admin/search/analytics/export?days=90
     *
     * Export search_analytics.csv từ Elasticsearch → dùng để train PhoBERT trên Colab.
     *
     * CSV columns (khớp với format Colab Cell 6 expect):
     *   id, user_id, query_text, query_normalized, clicked_product_id,
     *   click_position, result_count, session_id, searched_at
     *
     * @param days số ngày lấy data (default 90 — RETENTION_DAYS của ES)
     */
    @GetMapping("/analytics/export")
    public ResponseEntity<String> exportSearchAnalytics(
            @RequestParam(defaultValue = "90") int days) {

        log.info("Admin exporting search analytics CSV from ES ({} days)...", days);
        try {
            List<Map<String, Object>> rows = searchAnalyticsEsService.exportAllAnalytics(days);

            StringBuilder csv = new StringBuilder('\uFEFF'); // UTF-8 BOM for Excel compat
            csv.append("id,user_id,query_text,query_normalized,clicked_product_id,")
               .append("click_position,result_count,session_id,searched_at\n");

            for (Map<String, Object> row : rows) {
                csv.append(csvField(row.get("id"))).append(',');
                csv.append(csvField(row.get("user_id"))).append(',');
                csv.append(csvField(row.get("query_text"))).append(',');
                csv.append(csvField(row.get("query_normalized"))).append(',');
                csv.append(csvField(row.get("clicked_product_id"))).append(',');
                csv.append(csvField(row.get("click_position"))).append(',');
                csv.append(csvField(row.get("result_count"))).append(',');
                csv.append(csvField(row.get("session_id"))).append(',');
                csv.append(csvField(row.get("searched_at"))).append('\n');
            }

            log.info("Search analytics export: {} rows, {} days", rows.size(), days);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=search_analytics.csv")
                    .header("Content-Type", "text/csv; charset=UTF-8")
                    .body(csv.toString());

        } catch (Exception e) {
            log.error("Error exporting search analytics: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body("Export failed: " + e.getMessage());
        }
    }

    /**
     * Escape a value for CSV output.
     * Null → empty. Strings with comma/quote/newline → wrapped in double-quotes.
     */
    private String csvField(Object value) {
        if (value == null) return "";
        String s = value.toString();
        if (s.contains(",") || s.contains("\"") || s.contains("\n")) {
            return '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }
}
