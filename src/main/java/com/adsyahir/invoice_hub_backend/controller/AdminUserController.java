package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.PageResponse;
import com.adsyahir.invoice_hub_backend.dto.UpdateUserRoleRequest;
import com.adsyahir.invoice_hub_backend.dto.UserSummaryDto;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.User;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Platform user administration — list every user and reassign roles.
 * Restricted to platform admins (the tenant:manage permission).
 */
@RestController
@RequestMapping("/users")
@PreAuthorize("hasAuthority('tenant:manage')")
public class AdminUserController {

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private UserRepo userRepo;

    @GetMapping
    public PageResponse<UserSummaryDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by("id").descending());

        Page<User> result = (search == null || search.isBlank())
                ? userRepo.findAll(pageable)
                : userRepo.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
                        search, search, pageable);

        List<UserSummaryDto> content = result.getContent().stream().map(this::toDto).toList();
        return new PageResponse<>(content, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<?> updateRole(@PathVariable Long id,
                                        @Valid @RequestBody UpdateUserRoleRequest request) {
        User user = userRepo.findById(id).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "User not found"));
        }
        Role role = roleRepo.findById(request.getRoleId()).orElse(null);
        if (role == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Role not found"));
        }
        user.setRole(role);
        userRepo.save(user);
        return ResponseEntity.ok(toDto(user));
    }

    private UserSummaryDto toDto(User u) {
        return new UserSummaryDto(
                u.getId(),
                u.getFullName(),
                u.getEmail(),
                u.getRole().getName(),
                u.getTenant() != null ? u.getTenant().getName() : null,
                u.getCreatedAt());
    }
}
