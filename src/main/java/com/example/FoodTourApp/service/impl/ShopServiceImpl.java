package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.ShopDTO.*;
import com.example.FoodTourApp.entity.Address;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.AddressRepository;
import com.example.FoodTourApp.repository.ShopRepository;
import com.example.FoodTourApp.service.ShopService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShopServiceImpl implements ShopService {

    private final ShopRepository shopRepository;
    private final AddressRepository addressRepository;

    @Override
    @Transactional
    public ShopResponseDTO createShop(CreateShopRequestDTO request, User seller) {
        log.info("Creating shop for seller: {}", seller.getId());

        // Kiểm tra business license và tax code đã tồn tại chưa
        if (request.getBusinessLicense() != null &&
            shopRepository.findByBusinessLicense(request.getBusinessLicense()).isPresent()) {
            throw new RuntimeException("Giấy phép kinh doanh đã được sử dụng");
        }

        if (request.getTaxCode() != null &&
            shopRepository.findByTaxCode(request.getTaxCode()).isPresent()) {
            throw new RuntimeException("Mã số thuế đã được sử dụng");
        }

        // Tạo Address entity từ DTO
        Address address = createAddressFromDTO(request.getAddress(), seller);
        address = addressRepository.save(address);

        // Tạo Shop entity
        Shop shop = new Shop();
        shop.setSeller(seller);
        shop.setShopName(request.getShopName());
        shop.setDescription(request.getDescription());
        shop.setLogoUrl(request.getLogoUrl());
        shop.setBannerUrl(request.getBannerUrl());
        shop.setAddress(address);
        shop.setPhone(request.getPhone());
        // Lấy email từ user đăng ký shop, nếu request có email thì dùng email đó
        shop.setEmail(request.getEmail() != null ? request.getEmail() : seller.getEmail());
        shop.setBusinessLicense(request.getBusinessLicense());
        shop.setTaxCode(request.getTaxCode());
        shop.setOpeningHours(request.getOpeningHours());
        shop.setRating(0.0);
        shop.setTotalReviews(0);
        shop.setIsVerified(false);
        shop.setIsActive(true);
        shop.setCreatedAt(LocalDateTime.now());
        shop.setUpdatedAt(LocalDateTime.now());

        shop = shopRepository.save(shop);

        log.info("Shop created successfully with id: {}", shop.getId());
        return mapToShopResponseDTO(shop);
    }

    @Override
    @Transactional
    public ShopResponseDTO updateShop(Integer shopId, UpdateShopRequestDTO request, User user) {
        log.info("Updating shop: {} by user: {}", shopId, user.getId());

        Shop shop;

        // Kiểm tra nếu là Admin thì có quyền sửa tất cả shop
        boolean isAdmin = user.getRole().getRoleName().equals(Role.RoleName.ADMIN);

        if (isAdmin) {
            // Admin có thể sửa bất kỳ shop nào
            shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
            log.info("Admin updating shop: {}", shopId);
        } else {
            // Seller chỉ có thể sửa shop của mình
            shop = shopRepository.findBySellerAndId(user, shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng hoặc bạn không có quyền chỉnh sửa"));
        }

        // Cập nhật thông tin shop
        if (request.getShopName() != null) {
            shop.setShopName(request.getShopName());
        }
        if (request.getDescription() != null) {
            shop.setDescription(request.getDescription());
        }
        if (request.getLogoUrl() != null) {
            shop.setLogoUrl(request.getLogoUrl());
        }
        if (request.getBannerUrl() != null) {
            shop.setBannerUrl(request.getBannerUrl());
        }
        if (request.getPhone() != null) {
            shop.setPhone(request.getPhone());
        }
        if (request.getEmail() != null) {
            shop.setEmail(request.getEmail());
        }
        if (request.getBusinessLicense() != null) {
            shop.setBusinessLicense(request.getBusinessLicense());
        }
        if (request.getTaxCode() != null) {
            shop.setTaxCode(request.getTaxCode());
        }
        if (request.getOpeningHours() != null) {
            shop.setOpeningHours(request.getOpeningHours());
        }
        if (request.getIsActive() != null) {
            shop.setIsActive(request.getIsActive());
        }

        // Cập nhật địa chỉ nếu có
        if (request.getAddress() != null) {
            Address address = shop.getAddress();
            updateAddressFromDTO(address, request.getAddress());
            addressRepository.save(address);
        }

        shop.setUpdatedAt(LocalDateTime.now());
        shop = shopRepository.save(shop);

        log.info("Shop updated successfully: {}", shopId);
        return mapToShopResponseDTO(shop);
    }

    @Override
    public ShopResponseDTO getShopById(Integer shopId) {
        log.info("Getting shop by id: {}", shopId);

        Shop shop = shopRepository.findById(shopId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));

        return mapToShopResponseDTO(shop);
    }

    @Override
    public List<ShopResponseDTO> getShopsBySeller(User seller) {
        log.info("Getting shops for seller: {}", seller.getId());

        List<Shop> shops = shopRepository.findBySeller(seller);
        return shops.stream()
                .map(this::mapToShopResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<ShopResponseDTO> getAllActiveShops(Pageable pageable) {
        log.info("Getting all active shops with pagination");
        Page<Shop> shops = shopRepository.findByIsActiveTrue(pageable);
        return shops.map(this::mapToShopResponseDTO);
    }


    @Override
    @Transactional
    public void deleteShop(Integer shopId, User user) {
        log.info("Deleting shop: {} by user: {}", shopId, user.getId());

        Shop shop;

        // Kiểm tra nếu là Admin thì có quyền xóa tất cả shop
        boolean isAdmin = user.getRole().getRoleName().equals(Role.RoleName.ADMIN);

        if (isAdmin) {
            // Admin có thể xóa bất kỳ shop nào
            shop = shopRepository.findById(shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng"));
            log.info("Admin deleting shop: {}", shopId);
        } else {
            // Seller chỉ có thể xóa shop của mình
            shop = shopRepository.findBySellerAndId(user, shopId)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy cửa hàng hoặc bạn không có quyền xóa"));
        }

        shop.setIsActive(false);
        shop.setUpdatedAt(LocalDateTime.now());
        shopRepository.save(shop);

        log.info("Shop deleted (soft delete) successfully: {}", shopId);
    }

    /**
     * Tạo Address entity từ AddressRequestDTO
     */
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
        address.setAddressType(Address.AddressType.shop);
        address.setCreatedAt(LocalDateTime.now());
        return address;
    }

    /**
     * Cập nhật Address entity từ AddressRequestDTO
     */
    private void updateAddressFromDTO(Address address, AddressRequestDTO dto) {
        if (dto.getAddressLine() != null) {
            address.setAddressLine(dto.getAddressLine());
        }
        if (dto.getWard() != null) {
            address.setWard(dto.getWard());
        }
        if (dto.getDistrict() != null) {
            address.setDistrict(dto.getDistrict());
        }
        if (dto.getCity() != null) {
            address.setCity(dto.getCity());
        }
        if (dto.getCountry() != null) {
            address.setCountry(dto.getCountry());
        }
        if (dto.getPostalCode() != null) {
            address.setPostalCode(dto.getPostalCode());
        }
        if (dto.getLatitude() != null) {
            address.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            address.setLongitude(dto.getLongitude());
        }
    }

    /**
     * Map Shop entity sang ShopResponseDTO
     */
    private ShopResponseDTO mapToShopResponseDTO(Shop shop) {
        ShopResponseDTO dto = new ShopResponseDTO();
        dto.setId(shop.getId());
        dto.setSellerId(shop.getSeller().getId());
        dto.setSellerName(shop.getSeller().getFullName());
        dto.setShopName(shop.getShopName());
        dto.setDescription(shop.getDescription());
        dto.setLogoUrl(shop.getLogoUrl());
        dto.setBannerUrl(shop.getBannerUrl());
        dto.setAddress(mapToAddressResponseDTO(shop.getAddress()));
        dto.setPhone(shop.getPhone());
        dto.setEmail(shop.getEmail());
        dto.setBusinessLicense(shop.getBusinessLicense());
        dto.setTaxCode(shop.getTaxCode());
        dto.setOpeningHours(shop.getOpeningHours());
        dto.setRating(shop.getRating());
        dto.setTotalReviews(shop.getTotalReviews());
        dto.setIsVerified(shop.getIsVerified());
        dto.setIsActive(shop.getIsActive());
        dto.setCreatedAt(shop.getCreatedAt());
        dto.setUpdatedAt(shop.getUpdatedAt());
        return dto;
    }

    /**
     * Map Address entity sang AddressResponseDTO
     */
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

        // Build full address
        StringBuilder fullAddress = new StringBuilder();
        if (address.getAddressLine() != null) fullAddress.append(address.getAddressLine());
        if (address.getWard() != null) fullAddress.append(", ").append(address.getWard());
        if (address.getDistrict() != null) fullAddress.append(", ").append(address.getDistrict());
        if (address.getCity() != null) fullAddress.append(", ").append(address.getCity());
        dto.setFullAddress(fullAddress.toString());

        return dto;
    }
}
