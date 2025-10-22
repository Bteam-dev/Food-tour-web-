package com.example.FoodTourApp.service;

import com.example.FoodTourApp.DTO.VariantTypeDTO.VariantTypeRequestDTO;
import com.example.FoodTourApp.DTO.VariantTypeDTO.VariantTypeResponseDTO;
import com.example.FoodTourApp.entity.VariantType;
import com.example.FoodTourApp.repository.VariantTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VariantTypeService {
    private final VariantTypeRepository variantTypeRepository;

    // Lấy tất cả variant types đang active
    public List<VariantTypeResponseDTO> getAllActiveVariantTypes() {
        return variantTypeRepository.findByIsActiveTrue()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Lấy tất cả variant types (Admin)
    public List<VariantTypeResponseDTO> getAllVariantTypes() {
        return variantTypeRepository.findAll()
                .stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    // Lấy variant type theo ID
    public VariantTypeResponseDTO getVariantTypeById(Integer id) {
        VariantType variantType = variantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant type not found with id: " + id));
        return convertToDTO(variantType);
    }

    // Tạo variant type mới (Admin)
    @Transactional
    public VariantTypeResponseDTO createVariantType(VariantTypeRequestDTO requestDTO) {
        // Kiểm tra trùng tên
        if (variantTypeRepository.existsByName(requestDTO.getName())) {
            throw new RuntimeException("Variant type with name '" + requestDTO.getName() + "' already exists");
        }

        VariantType variantType = new VariantType();
        variantType.setName(requestDTO.getName());
        variantType.setDescription(requestDTO.getDescription());
        variantType.setIsActive(true);

        VariantType savedVariantType = variantTypeRepository.save(variantType);
        return convertToDTO(savedVariantType);
    }

    // Cập nhật variant type (Admin)
    @Transactional
    public VariantTypeResponseDTO updateVariantType(Integer id, VariantTypeRequestDTO requestDTO) {
        VariantType variantType = variantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant type not found with id: " + id));

        // Kiểm tra trùng tên (nếu đổi tên)
        if (!variantType.getName().equals(requestDTO.getName())
                && variantTypeRepository.existsByName(requestDTO.getName())) {
            throw new RuntimeException("Variant type with name '" + requestDTO.getName() + "' already exists");
        }

        variantType.setName(requestDTO.getName());
        variantType.setDescription(requestDTO.getDescription());

        VariantType updatedVariantType = variantTypeRepository.save(variantType);
        return convertToDTO(updatedVariantType);
    }

    // Xóa mềm variant type (Admin)
    @Transactional
    public void deleteVariantType(Integer id) {
        VariantType variantType = variantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant type not found with id: " + id));
        variantType.setIsActive(false);
        variantTypeRepository.save(variantType);
    }

    // Kích hoạt lại variant type (Admin)
    @Transactional
    public VariantTypeResponseDTO activateVariantType(Integer id) {
        VariantType variantType = variantTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Variant type not found with id: " + id));
        variantType.setIsActive(true);
        VariantType activatedVariantType = variantTypeRepository.save(variantType);
        return convertToDTO(activatedVariantType);
    }

    // Convert entity to DTO
    private VariantTypeResponseDTO convertToDTO(VariantType variantType) {
        VariantTypeResponseDTO dto = new VariantTypeResponseDTO();
        dto.setId(variantType.getId());
        dto.setName(variantType.getName());
        dto.setDescription(variantType.getDescription());
        dto.setIsActive(variantType.getIsActive());
        dto.setCreatedAt(variantType.getCreatedAt());
        dto.setUpdatedAt(variantType.getUpdatedAt());
        return dto;
    }
}

