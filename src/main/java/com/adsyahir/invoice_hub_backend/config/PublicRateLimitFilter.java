package com.adsyahir.invoice_hub_backend.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Fixed-window rate limiter for the unauthenticated public endpoints (/public/**), backed by
 * Redis. The pay-by-link surface has no login in front of it, so without this a single client
 * could hammer it freely.
 *
 * <p>Algorithm: INCR a per-client key, set a TTL on the first hit, reject once the count passes
 * the limit. INCR is atomic, so concurrent requests can't race the counter — the whole reason
 * to keep this in Redis rather than an in-process map (which also wouldn't survive more than one
 * app instance).
 */
@Component
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PublicRateLimitFilter.class);

    private static final int LIMIT = 30;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final String KEY_PREFIX = "ratelimit:public:";

    private final StringRedisTemplate redis;

    public PublicRateLimitFilter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String key = KEY_PREFIX + clientIp(request);

        Long count;
        try {
            count = redis.opsForValue().increment(key);   // atomic; the key is created at 1
            if (count != null && count == 1L) {
                // Set the TTL ONLY on the first request of a window. Doing it every request would
                // slide the expiry forward forever and the window would never reset.
                redis.expire(key, WINDOW);
            }
        } catch (Exception e) {
            // Fail OPEN: if Redis is down, let the request through rather than block payments.
            // A rate limiter outage must not become a payments outage.
            log.warn("Rate-limit check skipped — Redis unavailable: {}", e.getMessage());
            chain.doFilter(request, response);
            return;
        }

        if (count != null && count > LIMIT) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
            response.getWriter().write("{\"error\":\"Too many requests. Try again shortly.\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Only guard the public surface. A OncePerRequestFilter @Component is registered in the
     * servlet chain for ALL requests, so this scoping is what keeps authenticated traffic out
     * of the limiter.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().contains("/public/");
    }

    /**
     * Client IP. request.getRemoteAddr() is correct for a direct connection; behind a reverse
     * proxy / load balancer it is the proxy's IP (so everyone would share one bucket). When you
     * deploy behind nginx or an LB, switch to the first hop of X-Forwarded-For AND make sure the
     * proxy sets it — trusting a client-supplied header lets an attacker forge a fresh IP per
     * request and bypass the limit entirely.
     */
    private static String clientIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
