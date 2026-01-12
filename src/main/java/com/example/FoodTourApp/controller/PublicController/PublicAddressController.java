package com.example.FoodTourApp.controller.PublicController;

import com.example.FoodTourApp.DTO.AddressDTO.AddressDetailDTO;
import com.example.FoodTourApp.DTO.AddressDTO.AddressSuggestionDTO;
import com.example.FoodTourApp.service.HereApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Public Address Controller - Các endpoint địa chỉ public
 */
@RestController
@RequestMapping("/api/public/addresses")
@RequiredArgsConstructor
@Slf4j
public class PublicAddressController {

    private final HereApiService hereApiService;

    /**
     * API autocomplete địa chỉ - dùng khi user đang nhập
     * GET /api/addresses/autocomplete?query=123 Nguyen Van Linh&limit=5
     */
    @GetMapping("/autocomplete")
    public ResponseEntity<List<AddressSuggestionDTO>> autocompleteAddress(
            @RequestParam String query,
            @RequestParam(required = false, defaultValue = "5") Integer limit,
            @RequestParam(required = false) String city) {

        log.info("Autocomplete address with query: {}, limit: {}, city: {}", query, limit, city);

        List<AddressSuggestionDTO> suggestions = hereApiService.autocompleteAddress(query, limit, city);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * API lấy chi tiết địa chỉ từ ID - sau khi user chọn từ danh sách autocomplete
     * GET /api/addresses/lookup?id=here:pds:place:...
     */
    @GetMapping("/lookup")
    public ResponseEntity<AddressDetailDTO> lookupAddress(@RequestParam String id) {
        log.info("Lookup address with id: {}", id);

        AddressDetailDTO detail = hereApiService.lookupAddress(id);
        return ResponseEntity.ok(detail);
    }

    /**
     * API geocode địa chỉ - chuyển text address thành tọa độ + thông tin chi tiết
     * POST /api/addresses/geocode
     * Body: {"address": "123 Nguyen Van Linh, Da Nang"}
     */
    @PostMapping("/geocode")
    public ResponseEntity<AddressDetailDTO> geocodeAddress(@RequestBody String address) {
        log.info("Geocode address: {}", address);

        AddressDetailDTO detail = hereApiService.geocodeAddress(address);
        return ResponseEntity.ok(detail);
    }

    /**
     * API geocode đơn giản với query param
     * GET /api/addresses/geocode?address=123 Nguyen Van Linh, Da Nang
     */
    @GetMapping("/geocode")
    public ResponseEntity<AddressDetailDTO> geocodeAddressGet(@RequestParam String address) {
        log.info("Geocode address (GET): {}", address);

        AddressDetailDTO detail = hereApiService.geocodeAddress(address);
        return ResponseEntity.ok(detail);
    }
}

