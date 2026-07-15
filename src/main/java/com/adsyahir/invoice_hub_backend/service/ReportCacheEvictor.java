package com.adsyahir.invoice_hub_backend.service;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Component;

/**
 * Evicts a tenant's cached reports (dashboard / revenue / aging) after any write that changes
 * an invoice's amount, status or dates.
 *
 * <p>This is a SEPARATE bean on purpose. @CacheEvict works through Spring's proxy, and a
 * self-invocation — a method calling another method on the same bean — bypasses the proxy, so
 * the annotation silently does nothing. Putting the eviction here, called from InvoiceService /
 * PaymentService, guarantees the call crosses the proxy boundary and actually fires.
 */
@Component
public class ReportCacheEvictor {

    @Caching(evict = {
            @CacheEvict(value = "dashboard", key = "#tenantId", condition = "#tenantId != null"),
            @CacheEvict(value = "revenue", key = "#tenantId", condition = "#tenantId != null"),
            @CacheEvict(value = "aging", key = "#tenantId", condition = "#tenantId != null")
    })
    public void evict(Long tenantId) {
        // Body intentionally empty — the annotations do the work. Guard against a null tenant
        // (e.g. a platform-level action) so the key expression never dereferences null.
    }
}
