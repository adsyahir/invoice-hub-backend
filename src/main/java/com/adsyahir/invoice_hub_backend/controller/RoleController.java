package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dao.PermissionRepo;
import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.PermissionDto;
import com.adsyahir.invoice_hub_backend.dto.RoleDto;
import com.adsyahir.invoice_hub_backend.dto.UpdateRolePermissionsRequest;
import com.adsyahir.invoice_hub_backend.model.Permission;
import com.adsyahir.invoice_hub_backend.model.Role;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RBAC management — list roles and edit the permissions each role grants.
 * Restricted to users with the platform-admin permission (super admin).
 */
@RestController
@RequestMapping("/roles")
@PreAuthorize("hasAuthority('tenant:manage')")
public class RoleController {

    @Autowired
    private PermissionRepo permissionRepo;

    @Autowired
    private RoleRepo roleRepo;


    @GetMapping
    public List<RoleDto> list() {
        return roleRepo.findAll().stream()
                .sorted(Comparator.comparing(Role::getName))
                .map(RoleController::toDto)
                .toList();
    }

    @PutMapping("/{id}/permissions")
    public ResponseEntity<?> updatePermissions(@PathVariable Long id,
                                               @Valid @RequestBody UpdateRolePermissionsRequest request) {
        Role role = roleRepo.findById(id).orElse(null);
        if (role == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", "Role not found"));
        }

        Set<Permission> permissions = new HashSet<>(permissionRepo.findAllById(request.getPermissionIds()));
        role.setPermissions(permissions);
        roleRepo.save(role);

        return ResponseEntity.ok(toDto(role));
    }

    static RoleDto toDto(Role role) {
        List<PermissionDto> permissions = role.getPermissions().stream()
                .sorted(Comparator.comparing(Permission::getName))
                .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                .toList();
        return new RoleDto(role.getId(), role.getName(), role.getDescription(), permissions);
    }
}
