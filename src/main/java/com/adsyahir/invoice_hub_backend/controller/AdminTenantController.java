package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.UpdateTenantStatusRequest;
import com.adsyahir.invoice_hub_backend.service.AdminTenantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Platform tenant administration (super-admin). Restricted to tenant:manage,
 * which only SUPER_ADMIN holds.
 */
@RestController
@RequestMapping("/admin/tenants")
@PreAuthorize("hasAuthority('tenant:manage')")
public class AdminTenantController {

    private final AdminTenantService adminTenantService;

    public AdminTenantController(AdminTenantService adminTenantService) {
        this.adminTenantService = adminTenantService;
    }

    /** Every organization on the platform with usage stats. */
    @GetMapping
    public ResponseEntity<?> list() {
        return ResponseEntity.ok(adminTenantService.list());
    }

    /** A single tenant by its public uuid. */
    @GetMapping("/{uuid}")
    public ResponseEntity<?> get(@PathVariable UUID uuid) {
        return ResponseEntity.ok(adminTenantService.get(uuid));
    }

    /** Suspend / reactivate / cancel a tenant. */
    @PatchMapping("/{uuid}/status")
    public ResponseEntity<?> updateStatus(@PathVariable UUID uuid,
                                          @Valid @RequestBody UpdateTenantStatusRequest request) {
        return ResponseEntity.ok(adminTenantService.updateStatus(uuid, request.getStatus()));
    }
}
