package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.AddressDTO.AddressDetailDTO;
import com.example.FoodTourApp.DTO.AddressDTO.AddressSuggestionDTO;
import com.example.FoodTourApp.service.HereApiService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
@Slf4j
public class HereApiServiceImpl implements HereApiService {

    @Value("${here.api.key}")
    private String hereApiKey;

    private static final String HERE_AUTOCOMPLETE_URL = "https://autosuggest.search.hereapi.com/v1/autosuggest";
    private static final String HERE_GEOCODE_URL = "https://geocode.search.hereapi.com/v1/geocode";
    private static final String HERE_LOOKUP_URL = "https://lookup.search.hereapi.com/v1/lookup";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public HereApiServiceImpl() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public List<AddressSuggestionDTO> autocompleteAddress(String query, Integer limit, String city) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(HERE_AUTOCOMPLETE_URL)
                    .queryParam("q", query)
                    .queryParam("limit", limit != null ? limit : 5)
                    .queryParam("at", "14.0583,108.2772") // Trung tâm địa lý Việt Nam (Tây Nguyên)
                    .queryParam("in", "countryCode:VNM") // Giới hạn toàn quốc Việt Nam
                    .queryParam("lang", "vi-VN")
                    .queryParam("apiKey", hereApiKey);

            String url = builder.toUriString();
            log.info("HERE Autocomplete URL: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");

            List<AddressSuggestionDTO> suggestions = new ArrayList<>();

            for (JsonNode item : items) {
                AddressSuggestionDTO suggestion = new AddressSuggestionDTO();

                // Title: hiển thị cho user chọn
                suggestion.setTitle(item.path("title").asText());
                suggestion.setId(item.path("id").asText());
                suggestion.setResultType(item.path("resultType").asText());

                // Lấy thông tin address từ item
                JsonNode address = item.path("address");
                if (address.isMissingNode()) {
                    continue;
                }

                // HERE API structure:
                // - houseNumber: số nhà
                // - street: tên đường
                // - district: phường/xã (VN structure)
                // - city: quận/huyện (khi có county) HOẶC tỉnh/thành (khi không có county)
                // - county: tỉnh/thành phố (administrative area)

                String houseNumber = address.path("houseNumber").asText(null);
                String street = address.path("street").asText(null);
                String districtHere = address.path("district").asText(null);
                String cityHere = address.path("city").asText(null);
                String countyHere = address.path("county").asText(null);
                String stateHere = address.path("state").asText(null);

                // Build addressLine từ số nhà + đường
                // Nếu không có thì để null (trường hợp chọn locality)
                suggestion.setAddressLine(buildAddressLineFromComponents(houseNumber, street));

                // Map theo cấu trúc VN: ward → district → city
                suggestion.setWard(districtHere);

                // Map district và city từ HERE API response
                mapDistrictAndCity(suggestion::setDistrict, suggestion::setCity, cityHere, countyHere, stateHere);

                suggestion.setCountry(address.path("countryName").asText("Vietnam"));
                suggestion.setPostalCode(address.path("postalCode").asText(null));

                // Lấy tọa độ
                JsonNode position = item.path("position");
                if (!position.isMissingNode()) {
                    suggestion.setLatitude(position.path("lat").asDouble());
                    suggestion.setLongitude(position.path("lng").asDouble());
                }

                suggestions.add(suggestion);
            }

            return suggestions;
        } catch (Exception e) {
            log.error("Error calling HERE Autocomplete API: ", e);
            throw new RuntimeException("Không thể tìm kiếm địa chỉ: " + e.getMessage());
        }
    }

    @Override
    public AddressDetailDTO lookupAddress(String addressId) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(HERE_LOOKUP_URL)
                    .queryParam("id", addressId)
                    .queryParam("lang", "vi-VN")
                    .queryParam("apiKey", hereApiKey);

            String url = builder.toUriString();
            log.info("HERE Lookup URL: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            log.info("HERE Lookup Response: {}", response);

            JsonNode root = objectMapper.readTree(response);

            JsonNode address = root.path("address");
            JsonNode position = root.path("position");

            // Lấy các giá trị từ HERE API
            String houseNumber = address.path("houseNumber").asText(null);
            String street = address.path("street").asText(null);
            String districtHere = address.path("district").asText(null);
            String cityHere = address.path("city").asText(null);
            String countyHere = address.path("county").asText(null);
            String stateHere = address.path("state").asText(null);

            AddressDetailDTO detail = new AddressDetailDTO();

            // addressLine: số nhà + đường (có thể null nếu chỉ chọn quận/huyện)
            detail.setAddressLine(buildAddressLineFromComponents(houseNumber, street));

            // ward: phường/xã
            detail.setWard(districtHere);

            // Map district và city từ HERE API response
            mapDistrictAndCity(detail::setDistrict, detail::setCity, cityHere, countyHere, stateHere);

            detail.setCountry(address.path("countryName").asText("Vietnam"));
            detail.setPostalCode(address.path("postalCode").asText(null));
            detail.setLatitude(position.path("lat").asDouble());
            detail.setLongitude(position.path("lng").asDouble());
            detail.setFullAddress(root.path("title").asText());

            log.info("Mapped AddressDetailDTO: {}", detail);
            return detail;
        } catch (Exception e) {
            log.error("Error calling HERE Lookup API: ", e);
            throw new RuntimeException("Không thể lấy chi tiết địa chỉ: " + e.getMessage());
        }
    }

    @Override
    public AddressDetailDTO geocodeAddress(String address) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(HERE_GEOCODE_URL)
                    .queryParam("q", address)
                    .queryParam("in", "countryCode:VNM")
                    .queryParam("lang", "vi-VN")
                    .queryParam("apiKey", hereApiKey);

            String url = builder.toUriString();
            log.info("HERE Geocode URL: {}", url);

            String response = restTemplate.getForObject(url, String.class);
            JsonNode root = objectMapper.readTree(response);
            JsonNode items = root.path("items");

            if (items.isEmpty()) {
                throw new RuntimeException("Không tìm thấy địa chỉ phù hợp");
            }

            JsonNode firstItem = items.get(0);
            JsonNode addressNode = firstItem.path("address");
            JsonNode position = firstItem.path("position");

            // Mapping tương tự
            String houseNumber = addressNode.path("houseNumber").asText(null);
            String street = addressNode.path("street").asText(null);
            String countyValue = addressNode.path("county").asText(null);
            String cityValue = addressNode.path("city").asText(null);
            String stateValue = addressNode.path("state").asText(null);

            AddressDetailDTO detail = new AddressDetailDTO();
            detail.setAddressLine(buildAddressLineFromComponents(houseNumber, street));
            detail.setWard(addressNode.path("district").asText(null));

            // Map district và city từ HERE API response
            mapDistrictAndCity(detail::setDistrict, detail::setCity, cityValue, countyValue, stateValue);

            detail.setCountry(addressNode.path("countryName").asText("Vietnam"));
            detail.setPostalCode(addressNode.path("postalCode").asText(null));
            detail.setLatitude(position.path("lat").asDouble());
            detail.setLongitude(position.path("lng").asDouble());
            detail.setFullAddress(firstItem.path("title").asText());

            return detail;
        } catch (Exception e) {
            log.error("Error calling HERE Geocode API: ", e);
            throw new RuntimeException("Không thể geocode địa chỉ: " + e.getMessage());
        }
    }

    /**
     * Map district (quận/huyện) và city (tỉnh/thành phố) từ HERE API response.
     *
     * HERE API trả về cấu trúc address cho Việt Nam:
     * - Trường hợp 1 (có county): city=quận/huyện, county=tỉnh/thành, state có thể trùng county
     * - Trường hợp 2 (không có county, có state): city=quận/huyện, state=tỉnh/thành
     * - Trường hợp 3 (chỉ có city): city=tỉnh/thành (district không xác định)
     *
     * @param setDistrict setter cho district (quận/huyện)
     * @param setCity     setter cho city (tỉnh/thành phố)
     * @param cityHere    giá trị "city" từ HERE API
     * @param countyHere  giá trị "county" từ HERE API (có thể null)
     * @param stateHere   giá trị "state" từ HERE API (có thể null)
     */
    private void mapDistrictAndCity(Consumer<String> setDistrict, Consumer<String> setCity,
                                    String cityHere, String countyHere, String stateHere) {
        if (countyHere != null && !countyHere.isEmpty()) {
            // county có giá trị -> city (HERE) = quận/huyện, county = tỉnh/thành
            setDistrict.accept(cityHere);
            setCity.accept(countyHere);
        } else if (stateHere != null && !stateHere.isEmpty()) {
            // Không có county nhưng có state -> city (HERE) = quận/huyện, state = tỉnh/thành
            setDistrict.accept(cityHere);
            setCity.accept(stateHere);
        } else {
            // Chỉ có city -> city = tỉnh/thành, district không xác định
            setDistrict.accept(null);
            setCity.accept(cityHere);
        }
    }

    /**
     * Xây dựng address line từ số nhà và tên đường
     */
    private String buildAddressLineFromComponents(String houseNumber, String street) {
        StringBuilder sb = new StringBuilder();
        if (houseNumber != null) {
            sb.append(houseNumber);
        }
        if (street != null) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(street);
        }
        return !sb.isEmpty() ? sb.toString() : null;
    }

    /**
     * Xây dựng address line từ HERE API response (deprecated - dùng buildAddressLineFromComponents)
     */
    private String buildAddressLine(JsonNode address) {
        return buildAddressLineFromComponents(
            address.path("houseNumber").asText(null),
            address.path("street").asText(null)
        );
    }

    /**
     * HERE Routing v8 API – tính khoảng cách đường đi thực tế giữa 2 toạ độ.
     * Dùng transport mode "scooter" (phù hợp giao đồ ăn).
     * Trả về null nếu gọi API thất bại để caller có thể fallback.
     */
    @Override
    public java.math.BigDecimal calculateRouteDistanceKm(double originLat, double originLng,
                                                          double destLat,   double destLng) {
        try {
            String url = UriComponentsBuilder
                    .fromUriString("https://router.hereapi.com/v8/routes")
                    .queryParam("transportMode", "scooter")
                    .queryParam("origin",      originLat + "," + originLng)
                    .queryParam("destination", destLat   + "," + destLng)
                    .queryParam("return",      "summary")
                    .queryParam("apiKey",      hereApiKey)
                    .toUriString();

            log.info("HERE Routing URL: {}", url);
            String response = restTemplate.getForObject(url, String.class);
            JsonNode root   = objectMapper.readTree(response);

            // routes[0].sections[0].summary.length  (metres)
            long metres = root.path("routes").get(0)
                              .path("sections").get(0)
                              .path("summary").path("length").asLong(0);

            if (metres <= 0) return null;

            return new java.math.BigDecimal(metres)
                    .divide(new java.math.BigDecimal("1000"), 2, java.math.RoundingMode.HALF_UP);
        } catch (Exception e) {
            log.warn("HERE Routing API failed – falling back to fixed fee: {}", e.getMessage());
            return null;
        }
    }
}
