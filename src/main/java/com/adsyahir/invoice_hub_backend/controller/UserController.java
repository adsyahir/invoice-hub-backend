package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.LoginRequest;
import com.adsyahir.invoice_hub_backend.dto.RegisterRequest;
import com.adsyahir.invoice_hub_backend.model.Permission;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.JwtService;
import com.adsyahir.invoice_hub_backend.service.RefreshTokenService;
import com.adsyahir.invoice_hub_backend.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class UserController {
    @Autowired
    private UserService service;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    AuthenticationManager authenticationManager;

    @Autowired
    private JwtService jwtService;

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    private static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 days

    /** Sets (or, with value=null + maxAge=0, clears) the httpOnly refresh cookie. */
    private void setRefreshCookie(HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure); // false on http dev, true behind HTTPS
        cookie.setPath("/api/auth");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {

        User user = service.registerOrganization(request);

        // Sign them straight in, just like login: short-lived access token in the
        // body, long-lived refresh token in an httpOnly cookie.
        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        setRefreshCookie(response, refreshToken, REFRESH_COOKIE_MAX_AGE);
        refreshTokenService.addRefreshToken(user, refreshToken);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Organization created successfully",
                "token", token,
                "user", Map.of(
                        "id", user.getId(),
                        "fullName", user.getFullName(),
                        "email", user.getEmail(),
                        "role", user.getRole().getName()
                ),
                "tenant", Map.of(
                        "uuid", user.getTenant().getUuid(),
                        "name", user.getTenant().getName(),
                        "slug", user.getTenant().getSlug(),
                        "plan", user.getTenant().getPlan(),
                        "status", user.getTenant().getStatus()
                ),
                "permissions", permissionNames(user)
        ));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest credentials, HttpServletResponse response) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(credentials.getEmail(), credentials.getPassword()));
        } catch (AuthenticationException ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid email or password"));
        }

        // Auth succeeded — load the persisted user (with tenant) for the response.
        User user = service.getByEmail(credentials.getEmail());

        String token = jwtService.generateToken(user.getEmail());
        String refreshToken = jwtService.generateRefreshToken(user.getEmail());

        setRefreshCookie(response, refreshToken, REFRESH_COOKIE_MAX_AGE);
        refreshTokenService.addRefreshToken(user, refreshToken);

        // Same shape as /register so the frontend can populate the session.
        Map<String, Object> body = new HashMap<>();
        body.put("message", "User login successfully");
        body.put("token", token);
        body.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().getName()
        ));
        body.put("permissions", permissionNames(user));
        if (user.getTenant() != null) {
            body.put("tenant", Map.of(
                    "uuid", user.getTenant().getUuid(),
                    "name", user.getTenant().getName(),
                    "slug", user.getTenant().getSlug(),
                    "plan", user.getTenant().getPlan(),
                    "status", user.getTenant().getStatus()
            ));
        }
        return ResponseEntity.ok(body);
    }

    /** The permission names granted by the user's role, sorted. */
    private List<String> permissionNames(User user) {
        return user.getRole().getPermissions().stream()
                .map(Permission::getName)
                .sorted()
                .toList();
    }

    /**
     * Current authenticated user, tenant, and the effective permission list —
     * used by the frontend to restore the session and gate UI by permission.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Not authenticated"));
        }

        User user = principal.getUser();

        // The role's permissions (drop the ROLE_* marker authority).
        List<String> permissions = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith("ROLE_"))
                .sorted()
                .toList();

        Map<String, Object> body = new HashMap<>();
        body.put("user", Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "role", user.getRole().getName()
        ));
        body.put("permissions", permissions);
        if (user.getTenant() != null) {
            body.put("tenant", Map.of(
                    "uuid", user.getTenant().getUuid(),
                    "name", user.getTenant().getName(),
                    "slug", user.getTenant().getSlug(),
                    "plan", user.getTenant().getPlan(),
                    "status", user.getTenant().getStatus()
            ));
        }
        return ResponseEntity.ok(body);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@CookieValue(value = "refreshToken", required = false) String refreshToken) {

        if (refreshToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Refresh token missing"));
        }

        if (!jwtService.isRefreshTokenValid(refreshToken)
                || !"refresh".equals(jwtService.extractType(refreshToken))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid or expired refresh token"));
        }

        // Must still exist server-side. Logout deletes it, so this is what makes
        // revocation real — a deleted/revoked token can no longer mint access tokens.
        if (refreshTokenService.findByToken(refreshToken).isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Session has been revoked. Please sign in again."));
        }

        String username = jwtService.extractUserName(refreshToken);
        String newAccessToken = jwtService.generateToken(username);

        return ResponseEntity.ok(Map.of("token", newAccessToken));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(
            @CookieValue(value = "refreshToken", required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken != null) {
            refreshTokenService.deleteByToken(refreshToken);
        }

        // Clear the cookie (same attributes, maxAge 0).
        setRefreshCookie(response, null, 0);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
