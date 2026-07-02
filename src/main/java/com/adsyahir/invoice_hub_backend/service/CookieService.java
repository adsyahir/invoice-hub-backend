package com.adsyahir.invoice_hub_backend.service;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Centralizes the httpOnly refresh-token cookie so every auth entry point sets it identically. */
@Service
public class CookieService {

    public static final int REFRESH_COOKIE_MAX_AGE = 7 * 24 * 60 * 60; // 7 days

    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    /** Sets (or, with value=null + maxAge=0, clears) the httpOnly refresh cookie. */
    public void setRefreshCookie(HttpServletResponse response, String value, int maxAge) {
        Cookie cookie = new Cookie("refreshToken", value);
        cookie.setHttpOnly(true);
        cookie.setSecure(cookieSecure); // false on http dev, true behind HTTPS
        cookie.setPath("/api/auth");
        cookie.setMaxAge(maxAge);
        cookie.setAttribute("SameSite", "Lax");
        response.addCookie(cookie);
    }
}
