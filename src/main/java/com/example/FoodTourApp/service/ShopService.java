package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ShopDTO.CreateShopRequestDTO;
import com.example.FoodTourApp.DTO.ShopDTO.ShopResponseDTO;
import com.example.FoodTourApp.DTO.ShopDTO.UpdateShopRequestDTO;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Interface cho Shop Service
 */
public interface ShopService {

    /**
     * Tạo cửa hàng mới (bao gồm cả địa chỉ)
     * @param request Thông tin shop và địa chỉ
     * @param seller User seller (lấy từ authentication)
     * @return Thông tin shop đã tạo
     */
    ShopResponseDTO createShop(CreateShopRequestDTO request, User seller);

    /**
     * Cập nhật thông tin cửa hàng
     * @param shopId ID của shop
     * @param request Thông tin cập nhật
     * @param seller User seller (lấy từ authentication)
     * @return Thông tin shop đã cập nhật
     */
    ShopResponseDTO updateShop(Integer shopId, UpdateShopRequestDTO request, User seller);

    /**
     * Lấy thông tin chi tiết cửa hàng
     * @param shopId ID của shop
     * @return Thông tin chi tiết shop
     */
    ShopResponseDTO getShopById(Integer shopId);

    /**
     * Lấy danh sách shop của seller
     * @param seller User seller
     * @return Danh sách shop
     */
    List<ShopResponseDTO> getShopsBySeller(User seller);

    /**
     * Lấy tất cả shop đang hoạt động - WITH PAGINATION
     */
    Page<ShopResponseDTO> getAllActiveShops(Pageable pageable);

    /**
     * Xóa shop (soft delete - set isActive = false)
     * @param shopId ID của shop
     * @param seller User seller
     */
    void deleteShop(Integer shopId, User seller);

    /**
     * Tạo shop + upload logo/banner trong một lần gọi duy nhất.
     * Controller không cần biết về FileStorageService.
     */
    ShopResponseDTO createShopWithImages(CreateShopRequestDTO request, User seller,
                                         MultipartFile logo, MultipartFile banner);

    /**
     * Cập nhật shop + upload logo/banner mới nếu có.
     */
    ShopResponseDTO updateShopWithImages(Integer shopId, UpdateShopRequestDTO request, User seller,
                                         MultipartFile logo, MultipartFile banner);

    /**
     * Tạo shop từ JSON string + upload logo/banner.
     * Controller chỉ truyền raw dataJson và files.
     */
    ShopResponseDTO createShopFromJson(String dataJson, User seller, MultipartFile logo, MultipartFile banner);

    /**
     * Cập nhật shop từ JSON string + upload logo/banner mới.
     */
    ShopResponseDTO updateShopFromJson(Integer shopId, String dataJson, User seller, MultipartFile logo, MultipartFile banner);
}
