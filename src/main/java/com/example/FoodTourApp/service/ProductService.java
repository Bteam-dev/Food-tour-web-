package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ProductService {

    // Tạo product (cho seller, kèm variants)
    ProductResponseDTO createProduct(CreateProductRequestDTO request, User seller);

    // Cập nhật product
    ProductResponseDTO updateProduct(Integer productId, UpdateProductRequestDTO request, User user);

    // Xóa product (soft delete - set isAvailable = false)
    void deleteProduct(Integer productId, User user);

    // Lấy product theo ID (bao gồm variants)
    ProductResponseDTO getProductById(Integer productId);

    // Lấy tất cả product của shop (cho seller hoặc admin)
    List<ProductResponseDTO> getProductsByShop(Shop shop);

    // Lấy tất cả product active của shop (public) - WITH PAGINATION
    Page<ProductResponseDTO> getActiveProductsByShop(Integer shopId, Pageable pageable);

    // Lấy tất cả product active (public) - WITH PAGINATION
    Page<ProductResponseDTO> getAllActiveProducts(Pageable pageable);

    // Lấy product theo category (public) - WITH PAGINATION
    Page<ProductResponseDTO> getProductsByCategory(Integer categoryId, Pageable pageable);

    // Thêm variant cho product
    VariantResponseDTO addVariant(Integer productId, CreateVariantRequestDTO request, User user);

    // Cập nhật variant
    VariantResponseDTO updateVariant(Integer variantId, UpdateVariantRequestDTO request, User user);

    // Xóa variant (soft delete - set isActive = false)
    void deleteVariant(Integer variantId, User user);

    //Lấy toàn bộ product kể cả active và không active
    List<ProductResponseDTO> getAllProducts();


    //Validate quyền sở hữu shop: user phải là chủ shop hoặc ADMIN.
    //Ném RuntimeException nếu không có quyền.
    void validateShopOwnership(Integer shopId, User user);


    //Kiểm tra sản phẩm có thuộc shop không.
    // Ném RuntimeException nếu không thuộc.
    void validateProductBelongsToShop(Integer productId, Integer shopId);

    //Tạo product từ JSON string + upload ảnh.
    //Controller chỉ cần truyền raw dataJson và files.
    ProductResponseDTO createProductWithImages(Integer shopId, String dataJson, MultipartFile[] images, User user);

    // Cập nhật product từ JSON string + upload ảnh mới.
    ProductResponseDTO updateProductWithImages(Integer shopId, Integer productId, String dataJson, MultipartFile[] images, User user);

    //Tìm sản phẩm theo tên (LIKE %keyword%)
    Page<ProductResponseDTO> getProductsByNameContaining(String keyword, Pageable pageable);

    /**
     * Lọc và sắp xếp sản phẩm nâng cao.
     *
     * @param sortBy    "rating_desc" | "rating_asc" | "best_selling" | "newest" | "price_asc" | "price_desc"
     * @param city      Tên thành phố (tùy chọn)
     * @param categoryId ID danh mục (tùy chọn)
     * @param pageable  Phân trang
     */
    Page<ProductResponseDTO> getFilteredProducts(String sortBy, String city, Integer categoryId, Pageable pageable);
}
