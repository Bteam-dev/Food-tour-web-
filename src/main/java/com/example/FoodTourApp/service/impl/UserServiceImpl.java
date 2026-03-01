package com.example.FoodTourApp.service.impl;

import com.example.FoodTourApp.DTO.UserDTO.CreateUserRequest;
import com.example.FoodTourApp.DTO.UserDTO.UpdateProfileRequest;
import com.example.FoodTourApp.DTO.UserDTO.UpdateUserByAdminRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.entity.Role;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.repository.RoleRepository;
import com.example.FoodTourApp.repository.UserRepository;
import com.example.FoodTourApp.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final FileStorageService fileStorageService;

    public UserServiceImpl(UserRepository userRepository,
                           RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder,
                           FileStorageService fileStorageService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.fileStorageService = fileStorageService;
    }

    //  PHẦN DÀNH CHO USER

    @Override
    public UserResponse getUserByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getUserByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @Override
    public UserResponse getMyProfile(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(Integer userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getAvatarUrl() != null) user.setAvatarUrl(request.getAvatarUrl());
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());

        if (request.getGender() != null) {
            try {
                user.setGender(User.Gender.valueOf(request.getGender().toLowerCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid gender value");
            }
        }

        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfileWithAvatar(Integer userId, UpdateProfileRequest request, MultipartFile avatar) {
        if (avatar != null && !avatar.isEmpty()) {
            try {
                String subfolderId = "user_" + userId;
                String avatarUrl = fileStorageService.storeFile(avatar, FileStorageService.FileCategory.USER_AVATAR, subfolderId);
                request.setAvatarUrl(avatarUrl);
                log.info("Avatar uploaded for user {}: {}", userId, avatarUrl);
            } catch (java.io.IOException e) {
                throw new RuntimeException("Không thể upload avatar: " + e.getMessage(), e);
            }
        }
        return updateProfile(userId, request);
    }

    @Override
    public boolean is2FAEnabled(String email) {
        // TODO: Implement 2FA logic - check if user has 2FA enabled
        return false;
    }

    @Override
    public boolean verify2FA(String email, String code) {
        // TODO: Implement 2FA verification logic
        return false;
    }

    //  PHẦN DÀNH CHO ADMIN

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToUserResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getUserById(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public void deleteUser(Integer id) {
        if (!userRepository.existsById(id)) {
            throw new EntityNotFoundException("User not found with id: " + id);
        }
        userRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void toggleUserActiveStatus(Integer id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));
        user.setIsActive(!user.getIsActive());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserByAdmin(Integer id, UpdateUserByAdminRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("User not found with id: " + id));

        if (request.getUsername() != null && !request.getUsername().equals(user.getUsername())) {
            if (userRepository.findByUsername(request.getUsername()).isPresent()) {
                throw new IllegalArgumentException("Username already exists");
            }
            user.setUsername(request.getUsername());
        }

        if (request.getEmail() != null && !request.getEmail().equals(user.getEmail())) {
            if (userRepository.findByEmail(request.getEmail()).isPresent()) {
                throw new IllegalArgumentException("Email already exists");
            }
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getDateOfBirth() != null) user.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) user.setGender(User.Gender.valueOf(request.getGender().toLowerCase()));
        if (request.getIsActive() != null) user.setIsActive(request.getIsActive());

        if (request.getRoleName() != null) {
            Role role = roleRepository.findByRoleName(Role.RoleName.valueOf(request.getRoleName()))
                    .orElseThrow(() -> new EntityNotFoundException("Role not found"));
            user.setRole(role);
        }

        user.setUpdatedAt(LocalDateTime.now());
        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse createUserByAdmin(CreateUserRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new IllegalArgumentException("Username already exists");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(request.getFullName());
        user.setPhone(request.getPhone());
        user.setDateOfBirth(request.getDateOfBirth());
        user.setGender(request.getGender() != null ? User.Gender.valueOf(request.getGender().toLowerCase()) : null);
        user.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        user.setEmailVerified(request.getEmailVerified() != null ? request.getEmailVerified() : false);
        user.setWalletBalance(BigDecimal.ZERO);

        Role role = roleRepository.findByRoleName(Role.RoleName.valueOf(request.getRoleName()))
                .orElseThrow(() -> new EntityNotFoundException("Role not found: " + request.getRoleName()));
        user.setRole(role);

        user = userRepository.save(user);
        return mapToUserResponse(user);
    }

    // =====================================
    // ⚙️ HÀM CHUNG / HỖ TRỢ NỘI BỘ
    // =====================================

    @Override
    public boolean existsByUserName(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }

    @Override
    @Transactional
    public void registerFcmToken(Integer userId, String fcmToken) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
        user.setFcmToken(fcmToken);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setFullName(user.getFullName());
        response.setPhone(user.getPhone());
        response.setAvatarUrl(user.getAvatarUrl());
        response.setDateOfBirth(user.getDateOfBirth());
        response.setGender(user.getGender() != null ? user.getGender().toString() : null);
        response.setRoleName(user.getRole().getRoleName().toString());
        response.setIsActive(user.getIsActive());
        response.setEmailVerified(user.getEmailVerified());
        response.setLastLogin(user.getLastLogin());
        return response;
    }
}
