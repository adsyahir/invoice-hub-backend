package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.LoginRequest;
import com.adsyahir.invoice_hub_backend.dto.RegisterRequest;
import com.adsyahir.invoice_hub_backend.dto.response.AuthResult;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.CookieService;
import com.adsyahir.invoice_hub_backend.service.JwtService;
import com.adsyahir.invoice_hub_backend.service.RefreshTokenService;
import com.adsyahir.invoice_hub_backend.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.server.ResponseStatusException;

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

    @Autowired
    private CookieService cookieService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request,
                                      HttpServletResponse response) {


        AuthResult result = service.registerAndIssueTokens(request);
        cookieService.setRefreshCookie(response, result.refreshToken(), CookieService.REFRESH_COOKIE_MAX_AGE);

        return ResponseEntity.status(HttpStatus.CREATED).body(result.auth());
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

        // Auth succeeded — load the persisted user (with tenant), then mint tokens
        // and build the same payload shape as /register.
        User user = service.getByEmail(credentials.getEmail());

        AuthResult result = service.issueTokens(user);
        cookieService.setRefreshCookie(response, result.refreshToken(), CookieService.REFRESH_COOKIE_MAX_AGE);

        return ResponseEntity.ok(result.auth());
    }

    /**
     * Current authenticated user, tenant, and the effective permission list —
     * used by the frontend to restore the session and gate UI by permission.
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        // Same raw shape as login/register, minus the token (no new token is issued here).
        return ResponseEntity.ok(service.toAuthResponse(principal.getUser(), null));
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
        cookieService.setRefreshCookie(response, null, 0);

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }
}
