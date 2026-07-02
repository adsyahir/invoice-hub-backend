package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.RegisterRequest;
import com.adsyahir.invoice_hub_backend.dto.response.AuthResponse;
import com.adsyahir.invoice_hub_backend.dto.response.AuthResult;
import com.adsyahir.invoice_hub_backend.model.Permission;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.exception.ValidationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class UserService {
    @Autowired
    private UserRepo repo;

    @Autowired
    private TenantRepo tenantRepo;

    @Autowired
    private RoleRepo roleRepo;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

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

    /**
     * Registers an organization and immediately signs the admin in: generates the
     * access + refresh tokens, persists the refresh token, and builds the auth
     * payload. The controller only has to write the refresh cookie.
     */
    @Transactional
    public AuthResult registerAndIssueTokens(RegisterRequest request) {
        return issueTokens(registerOrganization(request));
    }

    /** Mints tokens for an already-authenticated user (login) and builds the payload. */
    @Transactional
    public AuthResult issueTokens(User user) {
        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());
        refreshTokenService.addRefreshToken(user, refreshToken);
        return new AuthResult(toAuthResponse(user, token), refreshToken);
    }

    /** Maps a user (+ its tenant/role/permissions) to the client-facing auth payload. */
    public AuthResponse toAuthResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .user(AuthResponse.UserSummary.builder()
                        .uuid(user.getUuid())
                        .fullName(user.getFullName())
                        .email(user.getEmail())
                        .role(user.getRole().getName())
                        .build())
                .tenant(user.getTenant() == null ? null : AuthResponse.TenantSummary.builder()
                        .uuid(user.getTenant().getUuid())
                        .name(user.getTenant().getName())
                        .slug(user.getTenant().getSlug())
                        .plan(user.getTenant().getPlan())
                        .status(user.getTenant().getStatus())
                        .build())
                .permissions(permissionNames(user))
                .build();
    }

    /** The permission names granted by the user's role, sorted. */
    public List<String> permissionNames(User user) {
        return user.getRole().getPermissions().stream()
                .map(Permission::getName)
                .sorted()
                .toList();
    }
}
