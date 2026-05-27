package com.example.FoodTourApp.controller.AdminController;

import com.example.FoodTourApp.DTO.UserDTO.CreateUserRequest;
import com.example.FoodTourApp.DTO.UserDTO.UpdateUserByAdminRequest;
import com.example.FoodTourApp.DTO.UserDTO.UserResponse;
import com.example.FoodTourApp.entity.User;
import com.example.FoodTourApp.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String role) {
        if ((name != null && !name.isBlank()) || (role != null && !role.isBlank())) {
            return ResponseEntity.ok(userService.searchUsers(name, role));
        }
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable Integer id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.ok(userService.createUserByAdmin(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponse> updateUser(@PathVariable Integer id,
                                                   @Valid @RequestBody UpdateUserByAdminRequest request,
                                                   @AuthenticationPrincipal User currentAdmin) {
        return ResponseEntity.ok(userService.updateUserByAdmin(id, request, currentAdmin.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Integer id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-active")
    public ResponseEntity<Void> toggleActive(@PathVariable Integer id,
                                             @AuthenticationPrincipal User currentAdmin) {
        userService.toggleUserActiveStatus(id, currentAdmin.getId());
        return ResponseEntity.ok().build();
    }
}
