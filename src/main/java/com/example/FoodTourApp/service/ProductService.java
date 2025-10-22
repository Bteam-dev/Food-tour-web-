package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.ProductDTO.CreateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductDTO.ProductResponseDTO;
import com.example.FoodTourApp.DTO.ProductDTO.UpdateProductRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.CreateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.UpdateVariantRequestDTO;
import com.example.FoodTourApp.DTO.ProductVariantDTO.VariantResponseDTO;
import com.example.FoodTourApp.entity.Shop;
import com.example.FoodTourApp.entity.User;

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

    // Lấy tất cả product active của shop (public)
    List<ProductResponseDTO> getActiveProductsByShop(Integer shopId);

    // Lấy tất cả product active (public)
    List<ProductResponseDTO> getAllActiveProducts();

    // Lấy product theo category (public)
    List<ProductResponseDTO> getProductsByCategory(Integer categoryId);

    // Thêm variant cho product
    VariantResponseDTO addVariant(Integer productId, CreateVariantRequestDTO request, User user);

    // Cập nhật variant
    VariantResponseDTO updateVariant(Integer variantId, UpdateVariantRequestDTO request, User user);

    // Xóa variant (soft delete - set isActive = false)
    void deleteVariant(Integer variantId, User user);

    //Lấy toàn bộ product kể cả active và không active
    List<ProductResponseDTO> getAllProducts();
}
