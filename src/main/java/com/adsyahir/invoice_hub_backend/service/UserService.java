package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.RegisterRequest;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class UserService {
    @Autowired
    private UserRepo repo;

    @Autowired
    private TenantRepo tenantRepo;

    @Autowired
    private RoleRepo roleRepo;

    private BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    /**
     * Provisions a new organization: creates the tenant and its first admin user
     * (TENANT_ADMIN) in a single transaction so we never leave a tenant without
     * an owner if user creation fails.
     */
    @Transactional
    public User registerOrganization(RegisterRequest req) {
        Map<String, String> errors = new HashMap<>();

        if (tenantRepo.existsBySlug(req.getSlug())) {
            errors.put("slug", "This subdomain is already taken");
        }
        if (repo.existsByEmail(req.getEmail())) {
            errors.put("email", "Email already exists!");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        Tenant tenant = new Tenant();
        tenant.setName(req.getOrgName());
        tenant.setSlug(req.getSlug());
        tenant = tenantRepo.save(tenant);

        // The org creator is the tenant administrator.
        Role adminRole = roleRepo.findByName("TENANT_ADMIN");

        User user = new User();
        user.setFullName(req.getFullName());
        user.setEmail(req.getEmail());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setRole(adminRole);
        user.setTenant(tenant);

        return repo.save(user);
    }

    /** Loads the persisted user (with tenant) by email — used after login auth. */
    public User getByEmail(String email) {
        return repo.findByEmail(email);
    }
}
