package com.adsyahir.invoice_hub_backend.cache;

import com.adsyahir.invoice_hub_backend.config.PublicRateLimitFilter;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The public-endpoint rate limiter, driven against a real Redis (its INCR/EXPIRE is the whole
 * mechanism, so mocking Redis would test nothing). The filter is exercised directly rather than
 * through MockMvc — that keeps the test about the limiter, not about routing or security.
 */
class PublicRateLimitIT extends AbstractIntegrationTest {

    private static final int LIMIT = 30;

    @Autowired
    private PublicRateLimitFilter filter;

    private MockHttpServletResponse fire(String ip, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    @DisplayName("allows up to the limit, then returns 429 with Retry-After")
    void blocksOverTheLimit() throws Exception {
        for (int i = 1; i <= LIMIT; i++) {
            assertThat(fire("10.0.0.1", "/api/public/invoices/tok").getStatus())
                    .as("request %d should be allowed", i)
                    .isEqualTo(200);
        }
        MockHttpServletResponse blocked = fire("10.0.0.1", "/api/public/invoices/tok");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    @DisplayName("the limit is per-IP — one client's flood doesn't block another")
    void limitIsPerClient() throws Exception {
        for (int i = 0; i < LIMIT + 5; i++) {
            fire("10.0.0.2", "/api/public/invoices/tok");   // exhaust client A
        }
        assertThat(fire("10.0.0.2", "/api/public/invoices/tok").getStatus()).isEqualTo(429);
        // A different IP has its own bucket, untouched.
        assertThat(fire("10.0.0.3", "/api/public/invoices/tok").getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("non-public paths are not rate-limited — the chain always proceeds")
    void skipsNonPublicPaths() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/invoices");
        request.setRemoteAddr("10.0.0.9");
        MockHttpServletResponse response = new MockHttpServletResponse();

        for (int i = 0; i < LIMIT + 10; i++) {
            filter.doFilter(request, response, chain);
        }
        // Never short-circuited: the chain ran every time.
        verify(chain, times(LIMIT + 10)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
