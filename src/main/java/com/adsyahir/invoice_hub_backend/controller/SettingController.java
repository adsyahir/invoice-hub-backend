package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.request.UpdateOrganizationRequest;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.SettingService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/settings")
public class SettingController {

    @Autowired
    private SettingService settingService;

    @PatchMapping("/{uuid}")
    public ResponseEntity<?> updateOrganization(@PathVariable UUID uuid, @Valid @RequestBody UpdateOrganizationRequest request,
                                                @AuthenticationPrincipal UserPrincipal principal) {
        if (!uuid.equals(principal.getUser().getTenant().getUuid())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found");
        }
        return ResponseEntity.ok(settingService.updateOrganization(uuid, request, principal.getUser().getId()));
    }

}
