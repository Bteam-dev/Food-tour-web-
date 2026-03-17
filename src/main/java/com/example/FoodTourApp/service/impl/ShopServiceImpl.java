package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ShopDTO.*;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.FCMService;
import com.example.FoodTourApp.service.ShopService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final FCMService fcmService;

    @Override
    @Transactional
    public ShopResponseDTO createShop(CreateShopRequestDTO request, User seller) {
        log.info("Creating shop for seller: {}", seller.getId());

        if (request.getTaxCode() != null &&
            shopRepository.findByTaxCode(request.getTaxCode()).isPresent()) {
            throw new RuntimeException("Mã số thuế đã được sử dụng");
        }

        Shop shop = new Shop();
        shop.setSeller(seller);
        shop.setShopName(request.getShopName());
        shop.setDescription(request.getDescription());
        shop.setLogoUrl(request.getLogoUrl());
        shop.setBannerUrl(request.getBannerUrl());
        shop.setPhone(request.getPhone());
        shop.setEmail(request.getEmail() != null ? request.getEmail() : seller.getEmail());
        shop.setTaxCode(request.getTaxCode());
        shop.setOpeningHours(request.getOpeningHours());
        shop.setRating(0.0);
        shop.setTotalReviews(0L);
        shop.setIsVerified(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setUpdatedAt(LocalDateTime.now());

        // Nhúng trực tiếp các trường địa chỉ từ DTO
        if (request.getAddress() != null) {
            AddressRequestDTO addr = request.getAddress();
            shop.setAddressLine(addr.getAddressLine());
            shop.setWard(addr.getWard());
            shop.setDistrict(addr.getDistrict());
            shop.setCity(addr.getCity());
            shop.setCountry(addr.getCountry() != null ? addr.getCountry() : "Vietnam");
            shop.setPostalCode(addr.getPostalCode());
            shop.setLatitude(addr.getLatitude());
            shop.setLongitude(addr.getLongitude());
        }

        shop = shopRepository.save(shop);
        log.info("Shop created successfully with id: {}", shop.getId());
        return mapToShopResponseDTO(shop);
    }

    @Override
    @Transactional
    public ShopResponseDTO updateShop(Integer shopId, UpdateShopRequestDTO request, User user) {
        log.info("Updating shop: {} by user: {}", shopId, user.getId());

        Shop shop;
        boolean isAdmin = user.getRole().getRoleName().equals(Role.RoleName.ADMIN);
        if (isAdmin) {
            shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
        } else {
            shop = shopRepository.findBySellerAndId(user, shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng hoặc bạn không có quyền chỉnh sửa"));
        }

        if (request.getShopName() != null) shop.setShopName(request.getShopName());
        if (request.getDescription() != null) shop.setDescription(request.getDescription());
        if (request.getLogoUrl() != null) shop.setLogoUrl(request.getLogoUrl());
        if (request.getBannerUrl() != null) shop.setBannerUrl(request.getBannerUrl());
        if (request.getPhone() != null) shop.setPhone(request.getPhone());
        if (request.getEmail() != null) shop.setEmail(request.getEmail());
        if (request.getTaxCode() != null) shop.setTaxCode(request.getTaxCode());
        if (request.getOpeningHours() != null) shop.setOpeningHours(request.getOpeningHours());
        if (request.getIsActive() != null) shop.setIsActive(request.getIsActive());

        // Cập nhật địa chỉ nhúng trực tiếp
        if (request.getAddress() != null) {
            AddressRequestDTO addr = request.getAddress();
            if (addr.getAddressLine() != null) shop.setAddressLine(addr.getAddressLine());
            if (addr.getWard() != null) shop.setWard(addr.getWard());
            if (addr.getDistrict() != null) shop.setDistrict(addr.getDistrict());
            if (addr.getCity() != null) shop.setCity(addr.getCity());
            if (addr.getCountry() != null) shop.setCountry(addr.getCountry());
            if (addr.getPostalCode() != null) shop.setPostalCode(addr.getPostalCode());
            if (addr.getLatitude() != null) shop.setLatitude(addr.getLatitude());
            if (addr.getLongitude() != null) shop.setLongitude(addr.getLongitude());
        }

        shop.setUpdatedAt(LocalDateTime.now());
        shop = shopRepository.save(shop);
        log.info("Shop updated successfully: {}", shopId);
        return mapToShopResponseDTO(shop);
    }

    @Override
    public ShopResponseDTO getShopById(Integer shopId) {
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
        return mapToShopResponseDTO(shop);
    }

    @Override
    public List<ShopResponseDTO> getShopsBySeller(User seller) {
        return shopRepository.findBySeller(seller).stream()
                .map(this::mapToShopResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ShopResponseDTO> getAllActiveShops(Pageable pageable) {
        // Chỉ trả về shop đã được admin duyệt (isVerified=true) VÀ đang hoạt động (isActive=true)
        return shopRepository.findByIsVerifiedTrueAndIsActiveTrue(pageable).map(this::mapToShopResponseDTO);
    }

    @Override
    @Transactional
    public void deleteShop(Integer shopId, User user) {
        log.info("Soft-deleting shop: {} by user: {}", shopId, user.getId());
        Shop shop;
        boolean isAdmin = user.getRole().getRoleName().equals(Role.RoleName.ADMIN);
        if (isAdmin) {
            shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
        } else {
            shop = shopRepository.findBySellerAndId(user, shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng hoặc bạn không có quyền xóa"));
        }
        shop.setIsActive(false);
        shop.setUpdatedAt(LocalDateTime.now());
        shopRepository.save(shop);
        log.info("Shop soft-deleted successfully: {}", shopId);
    }

    @Override
    @Transactional
    public ShopResponseDTO createShopWithImages(CreateShopRequestDTO request, User seller,
                                                MultipartFile logo, MultipartFile banner,
                                                MultipartFile[] businessLicenseImages) {
        // Validate giấy phép kinh doanh trước
        if (businessLicenseImages == null || businessLicenseImages.length == 0) {
            throw new RuntimeException("Ảnh giấy phép kinh doanh là bắt buộc");
        }

        if (request.getTaxCode() != null &&
            shopRepository.findByTaxCode(request.getTaxCode()).isPresent()) {
            throw new RuntimeException("Mã số thuế đã được sử dụng");
        }

        // Dùng temp folder để upload ảnh trước khi có shopId
        String tempFolder = "temp_" + seller.getId() + "_" + System.currentTimeMillis();

        // Upload ảnh giấy phép kinh doanh TRƯỚC
        List<String> businessLicenseUrls;
        try {
            businessLicenseUrls = fileStorageService.storeFiles(
                    businessLicenseImages,
                    FileStorageService.FileCategory.BUSINESS_LICENSE,
                    tempFolder
            );
            if (businessLicenseUrls.isEmpty()) {
                throw new RuntimeException("Ảnh giấy phép kinh doanh là bắt buộc");
            }
        } catch (IOException e) {
            throw new RuntimeException("Không thể upload ảnh giấy phép kinh doanh: " + e.getMessage(), e);
        }

        // Upload logo (nếu có)
        String logoUrl = request.getLogoUrl();
        if (logo != null && !logo.isEmpty()) {
            try {
                logoUrl = fileStorageService.storeFile(logo, FileStorageService.FileCategory.SHOP_LOGO, tempFolder);
            } catch (IOException e) {
                throw new RuntimeException("Không thể upload logo: " + e.getMessage(), e);
            }
        }

        // Upload banner (nếu có)
        String bannerUrl = request.getBannerUrl();
        if (banner != null && !banner.isEmpty()) {
            try {
                bannerUrl = fileStorageService.storeFile(banner, FileStorageService.FileCategory.SHOP_BANNER, tempFolder);
            } catch (IOException e) {
                throw new RuntimeException("Không thể upload banner: " + e.getMessage(), e);
            }
        }

        // Tạo Shop entity với đầy đủ dữ liệu, save 1 lần duy nhất
        log.info("Creating shop for seller: {}", seller.getId());
        Shop shop = new Shop();
        shop.setSeller(seller);
        shop.setShopName(request.getShopName());
        shop.setDescription(request.getDescription());
        shop.setLogoUrl(logoUrl);
        shop.setBannerUrl(bannerUrl);
        shop.setPhone(request.getPhone());
        shop.setEmail(request.getEmail() != null ? request.getEmail() : seller.getEmail());
        shop.setTaxCode(request.getTaxCode());
        shop.setOpeningHours(request.getOpeningHours());
        shop.setRating(0.0);
        shop.setTotalReviews(0L);
        shop.setIsVerified(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setUpdatedAt(LocalDateTime.now());

        if (request.getAddress() != null) {
            AddressRequestDTO addr = request.getAddress();
            shop.setAddressLine(addr.getAddressLine());
            shop.setWard(addr.getWard());
            shop.setDistrict(addr.getDistrict());
            shop.setCity(addr.getCity());
            shop.setCountry(addr.getCountry() != null ? addr.getCountry() : "Vietnam");
            shop.setPostalCode(addr.getPostalCode());
            shop.setLatitude(addr.getLatitude());
            shop.setLongitude(addr.getLongitude());
        }

        try {
            shop.setBusinessLicenseImageUrls(objectMapper.writeValueAsString(businessLicenseUrls));
        } catch (IOException e) {
            throw new RuntimeException("Lỗi serialize danh sách ảnh giấy phép: " + e.getMessage(), e);
        }

        shop = shopRepository.save(shop);
        log.info("Shop created successfully with id: {}", shop.getId());
        return mapToShopResponseDTO(shop);
    }

    @Override
    @Transactional
    public ShopResponseDTO updateShopWithImages(Integer shopId, UpdateShopRequestDTO request, User seller,
                                                MultipartFile logo, MultipartFile banner,
                                                MultipartFile[] businessLicenseImages) {
        String subfolderId = "shop_" + shopId;

        // Upload logo mới
        if (logo != null && !logo.isEmpty()) {
            try {
                request.setLogoUrl(fileStorageService.storeFile(logo, FileStorageService.FileCategory.SHOP_LOGO, subfolderId));
            } catch (IOException e) {
                throw new RuntimeException("Không thể upload logo: " + e.getMessage(), e);
            }
        }

        // Upload banner mới
        if (banner != null && !banner.isEmpty()) {
            try {
                request.setBannerUrl(fileStorageService.storeFile(banner, FileStorageService.FileCategory.SHOP_BANNER, subfolderId));
            } catch (IOException e) {
                throw new RuntimeException("Không thể upload banner: " + e.getMessage(), e);
            }
        }

        // Upload ảnh giấy phép kinh doanh mới (nếu có)
        if (businessLicenseImages != null && businessLicenseImages.length > 0) {
            try {
                List<String> businessLicenseUrls = fileStorageService.storeFiles(
                        businessLicenseImages,
                        FileStorageService.FileCategory.BUSINESS_LICENSE,
                        subfolderId
                );
                if (!businessLicenseUrls.isEmpty()) {
                    Shop shop = shopRepository.findById(shopId)
                            .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
                    shop.setBusinessLicenseImageUrls(objectMapper.writeValueAsString(businessLicenseUrls));
                    shopRepository.save(shop);
                }
            } catch (IOException e) {
                throw new RuntimeException("Không thể upload ảnh giấy phép kinh doanh: " + e.getMessage(), e);
            }
        }

        return updateShop(shopId, request, seller);
    }

    @Override
    @Transactional
    public ShopResponseDTO createShopFromJson(String dataJson, User seller,
                                              MultipartFile logo, MultipartFile banner,
                                              MultipartFile[] businessLicenseImages) {
        if (dataJson == null || dataJson.isEmpty()) throw new RuntimeException("Thiếu dữ liệu shop");
        try {
            CreateShopRequestDTO request = objectMapper.readValue(dataJson, CreateShopRequestDTO.class);
            return createShopWithImages(request, seller, logo, banner, businessLicenseImages);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dữ liệu shop không hợp lệ: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ShopResponseDTO updateShopFromJson(Integer shopId, String dataJson, User seller,
                                              MultipartFile logo, MultipartFile banner,
                                              MultipartFile[] businessLicenseImages) {
        if (dataJson == null || dataJson.isEmpty()) throw new RuntimeException("Thiếu dữ liệu cập nhật");
        try {
            UpdateShopRequestDTO request = objectMapper.readValue(dataJson, UpdateShopRequestDTO.class);
            return updateShopWithImages(shopId, request, seller, logo, banner, businessLicenseImages);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Dữ liệu cập nhật không hợp lệ: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public ShopResponseDTO approveShop(Integer shopId) {
        log.info("Admin approving shop: {}", shopId);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
        shop.setIsVerified(true);
        shop.setIsActive(true);
        shop.setUpdatedAt(LocalDateTime.now());
        shop = shopRepository.save(shop);
        log.info("Shop {} approved successfully", shopId);

        // Gửi FCM notification cho seller
        fcmService.sendShopApprovedNotification(shop.getSeller(), shop.getShopName());
        return mapToShopResponseDTO(shop);
    }

    @Override
    @Transactional
    public ShopResponseDTO rejectShop(Integer shopId, String reason) {
        log.info("Admin rejecting shop: {}", shopId);
        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
        shop.setIsVerified(false);
        shop.setIsActive(false);
        shop.setRejectionReason(reason);
        shop.setUpdatedAt(LocalDateTime.now());
        shop = shopRepository.save(shop);
        log.info("Shop {} rejected. Reason: {}", shopId, reason);

        // Gửi FCM notification cho seller
        fcmService.sendShopRejectedNotification(shop.getSeller(), shop.getShopName(), reason);
        return mapToShopResponseDTO(shop);
    }

    @Override
    public Page<ShopResponseDTO> getAllShopsForAdmin(Pageable pageable) {
        return shopRepository.findAll(pageable).map(this::mapToShopResponseDTO);
    }

    private ShopResponseDTO mapToShopResponseDTO(Shop shop) {
        ShopResponseDTO dto = new ShopResponseDTO();
        dto.setId(shop.getId());
        dto.setSellerId(shop.getSeller().getId());
        dto.setSellerName(shop.getSeller().getFullName());
        dto.setShopName(shop.getShopName());
        dto.setDescription(shop.getDescription());
        dto.setLogoUrl(shop.getLogoUrl());
        dto.setBannerUrl(shop.getBannerUrl());
        dto.setPhone(shop.getPhone());
        dto.setEmail(shop.getEmail());

        // Parse JSON array ảnh giấy phép kinh doanh
        try {
            if (shop.getBusinessLicenseImageUrls() != null) {
                List<String> imageUrls = objectMapper.readValue(
                        shop.getBusinessLicenseImageUrls(),
                        new TypeReference<List<String>>() {}
                );
                dto.setBusinessLicenseImageUrls(imageUrls);
            } else {
                dto.setBusinessLicenseImageUrls(List.of());
            }
        } catch (Exception e) {
            log.warn("Failed to parse businessLicenseImageUrls for shop {}: {}", shop.getId(), e.getMessage());
            dto.setBusinessLicenseImageUrls(List.of());
        }

        dto.setTaxCode(shop.getTaxCode());
        dto.setOpeningHours(shop.getOpeningHours());
        dto.setRating(shop.getRating());
        dto.setTotalReviews(shop.getTotalReviews());
        dto.setIsVerified(shop.getIsVerified());
        dto.setIsActive(shop.getIsActive());
        dto.setRejectionReason(shop.getRejectionReason());
        dto.setCreatedAt(shop.getCreatedAt());
        dto.setUpdatedAt(shop.getUpdatedAt());

        // Map địa chỉ nhúng sang AddressResponseDTO
        AddressResponseDTO addrDto = new AddressResponseDTO();
        addrDto.setAddressLine(shop.getAddressLine());
        addrDto.setWard(shop.getWard());
        addrDto.setDistrict(shop.getDistrict());
        addrDto.setCity(shop.getCity());
        addrDto.setCountry(shop.getCountry());
        addrDto.setPostalCode(shop.getPostalCode());
        addrDto.setLatitude(shop.getLatitude());
        addrDto.setLongitude(shop.getLongitude());
        StringBuilder full = new StringBuilder();
        if (shop.getAddressLine() != null) full.append(shop.getAddressLine());
        if (shop.getWard() != null) full.append(", ").append(shop.getWard());
        if (shop.getDistrict() != null) full.append(", ").append(shop.getDistrict());
        if (shop.getCity() != null) full.append(", ").append(shop.getCity());
        addrDto.setFullAddress(full.toString());
        dto.setAddress(addrDto);

        return dto;
    }
}
