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
                    .queryParam("at", "16.0544,108.2022") // Tọa độ trung tâm Việt Nam (Đà Nẵng)
                    .queryParam("in", "countryCode:VNM") // Chỉ tìm ở Việt Nam
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

                // Build addressLine từ số nhà + đường
                // Nếu không có thì để null (trường hợp chọn locality)
                suggestion.setAddressLine(buildAddressLineFromComponents(houseNumber, street));

                // Map theo cấu trúc VN: ward → district → city
                suggestion.setWard(districtHere);

                // Nếu có county: city=quận/huyện, county=tỉnh/thành
                // Nếu không có county: city=tỉnh/thành
                if (countyHere != null && !countyHere.isEmpty()) {
                    suggestion.setDistrict(cityHere);
                    suggestion.setCity(countyHere);
                } else {
                    suggestion.setDistrict(null);
                    suggestion.setCity(cityHere);
                }

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

            AddressDetailDTO detail = new AddressDetailDTO();

            // addressLine: số nhà + đường (có thể null nếu chỉ chọn quận/huyện)
            detail.setAddressLine(buildAddressLineFromComponents(houseNumber, street));

            // ward: phường/xã
            detail.setWard(districtHere);

            // district và city mapping theo logic:
            // Nếu có county: city (HERE) = district (VN), county (HERE) = city (VN)
            // Nếu không có county: city (HERE) = city (VN)
            if (countyHere != null && !countyHere.isEmpty()) {
                detail.setDistrict(cityHere);    // "Quận Cầu Giấy"
                detail.setCity(countyHere);      // "Hà Nội"
            } else {
                detail.setDistrict(null);
                detail.setCity(cityHere);
            }

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

            AddressDetailDTO detail = new AddressDetailDTO();
            detail.setAddressLine(buildAddressLineFromComponents(houseNumber, street));
            detail.setWard(addressNode.path("district").asText(null));

            if (cityValue != null && countyValue != null) {
                detail.setDistrict(cityValue);
                detail.setCity(countyValue);
            } else if (cityValue != null) {
                detail.setCity(cityValue);
                detail.setDistrict(null);
            } else if (countyValue != null) {
                detail.setCity(countyValue);
                detail.setDistrict(null);
            }

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
}
