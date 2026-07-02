package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.request.UpdateOrganizationRequest;
import com.adsyahir.invoice_hub_backend.dto.response.TeamMemberResponse;
import com.adsyahir.invoice_hub_backend.dto.response.TenantResponse;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class SettingService {

    @Autowired
    private TenantRepo tenantRepo;

    @Autowired
    private UserRepo userRepo;


    @Transactional
    public TenantResponse updateOrganization(UUID uuid, UpdateOrganizationRequest request, Long userId) {

        Tenant tenant = tenantRepo.findByUuid(uuid)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));

        tenant.setName(request.getOrganizationName());
        tenant.setSlug(request.getWorkspaceSubdomain());
        tenant.setDefaultCurrency(request.getDefaultCurrency());
        tenant.setBillingEmail(request.getBillingEmail());
        tenant.setTaxId(request.getTaxId());

        return toResponse(tenantRepo.save(tenant));
    }

    @Transactional(readOnly = true)
    public List<TeamMemberResponse> teamList(Long tenantId){
        return userRepo.findAllByTenantId(tenantId).stream()
                .map(this::toTeamMember)
                .toList();
    }

    private TeamMemberResponse toTeamMember(User u) {
        return TeamMemberResponse.builder()
                .id(u.getId())              // public handle (add uuid to User if not there)
                .fullName(u.getFullName())
                .email(u.getEmail())
                .role(u.getRole().getName())    // role name string
                .build();
    }

    private TenantResponse toResponse(Tenant t) {
        return TenantResponse.builder()
                .uuid(t.getUuid())
                .name(t.getName())
                .slug(t.getSlug())
                .plan(t.getPlan())
                .billingEmail(t.getBillingEmail())
                .defaultCurrency(t.getDefaultCurrency())
                .taxId(t.getTaxId())
                .maxUsers(t.getMaxUsers())
                .maxInvoicesPerMonth(t.getMaxInvoicesPerMonth())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}
