package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.response.AdminTenantResponse;
import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Platform administration of tenants (super-admin only). Lists every organization
 * with derived usage stats and toggles a tenant's status (US-004). All endpoints
 * are guarded by tenant:manage at the controller.
 */
@Service
public class AdminTenantService {

    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;
    private final InvoiceRepo invoiceRepo;

    public AdminTenantService(TenantRepo tenantRepo, UserRepo userRepo, InvoiceRepo invoiceRepo) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.invoiceRepo = invoiceRepo;
    }

    @Transactional(readOnly = true)
    public List<AdminTenantResponse> list() {
        LocalDateTime monthStart = startOfMonth();
        return tenantRepo.findAll().stream()
                .sorted(Comparator.comparing(Tenant::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(t -> toResponse(t, monthStart))
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminTenantResponse get(UUID uuid) {
        Tenant tenant = tenantRepo.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        return toResponse(tenant, startOfMonth());
    }

    @Transactional
    public AdminTenantResponse updateStatus(UUID uuid, TenantStatus status) {
        Tenant tenant = tenantRepo.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenant.setStatus(status);
        tenant.setUpdatedAt(LocalDateTime.now());
        return toResponse(tenantRepo.save(tenant), startOfMonth());
    }

    private LocalDateTime startOfMonth() {
        return LocalDate.now().withDayOfMonth(1).atStartOfDay();
    }

    private AdminTenantResponse toResponse(Tenant t, LocalDateTime monthStart) {
        long userCount = userRepo.countByTenantIdAndDeletedAtIsNull(t.getId());
        long invoicesThisMonth = invoiceRepo.countByTenantIdAndCreatedAtAfter(t.getId(), monthStart);
        return AdminTenantResponse.builder()
                .uuid(t.getUuid())
                .name(t.getName())
                .slug(t.getSlug())
                .plan(t.getPlan())
                .status(t.getStatus())
                .tin(t.getTaxId())
                .maxUsers(t.getMaxUsers())
                .maxInvoicesPerMonth(t.getMaxInvoicesPerMonth())
                .userCount(userCount)
                .invoicesThisMonth(invoicesThisMonth)
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
