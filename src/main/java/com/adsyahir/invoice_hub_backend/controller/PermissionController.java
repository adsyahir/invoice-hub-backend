package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dao.PermissionRepo;
import com.adsyahir.invoice_hub_backend.dto.PermissionDto;
import com.adsyahir.invoice_hub_backend.model.Permission;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;

/** The full permission catalog — used by the RBAC management UI. Super admin only. */
@RestController
@RequestMapping("/permissions")
@PreAuthorize("hasAuthority('tenant:manage')")
public class PermissionController {

    @Autowired
    private PermissionRepo permissionRepo;

    @GetMapping
    public List<PermissionDto> list() {
        return permissionRepo.findAll().stream()
                .sorted(Comparator.comparing(Permission::getName))
                .map(p -> new PermissionDto(p.getId(), p.getName(), p.getDescription()))
                .toList();
    }
}
