package com.adsyahir.invoice_hub_backend.cache;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.DashboardStats;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.service.ReportService;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Redis report caching: the second read is served from cache, and a write evicts it.
 *
 * <p>The DB is the thing being avoided, so the assertions are on repository calls — a spy on
 * InvoiceRepo — not on the returned numbers. "Same numbers" would pass even with caching off;
 * "the second call did not hit the DB" is what actually proves the cache.
 */
class ReportCacheIT extends AbstractIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @MockitoSpyBean
    private InvoiceRepo invoiceRepo;

    private Tenant tenant;
    private User accountant;
    private Client client;

    @BeforeEach
    void seedTenant() {
        tenant = fixtures.tenant("Acme Sdn Bhd", "acme");
        accountant = fixtures.user("accountant@acme.test", tenant,
                fixtures.role("ACCOUNTANT", "invoice:read", "invoice:write", "invoice:void",
                        "payment:read", "payment:record", "payment:refund", "report:read"));
        client = fixtures.client(tenant, "Nexus Digital", "billing@nexus.test");
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-2026-0001"), accountant);
        clearInvocations(invoiceRepo);   // ignore the repo calls made during seeding
    }

    @Test
    @DisplayName("the second dashboard read is served from cache, not the database")
    void secondReadIsCached() {
        reportService.dashboard(accountant);   // MISS -> queries the DB, populates the cache
        reportService.dashboard(accountant);   // HIT  -> must not touch the DB

        verify(invoiceRepo, atLeastOnce().description("first call must hit the DB"))
                .findAllInvoiceByTenantId(tenant.getId());
        // Exactly one query across both calls: the second was served from Redis.
        verify(invoiceRepo).findAllInvoiceByTenantId(tenant.getId());
    }

    @Test
    @DisplayName("recording a payment evicts the cache, so the next read recomputes")
    void writeEvictsCache() {
        reportService.dashboard(accountant);              // populate
        clearInvocations(invoiceRepo);

        invoiceService.send(getOnlyInvoice().getUuid(), accountant);
        paymentService.create(
                fixtures.paymentRequest(getOnlyInvoice().getUuid(), "1080.00"), accountant);

        DashboardStats after = reportService.dashboard(accountant);   // must recompute (evicted)

        verify(invoiceRepo, atLeastOnce()).findAllInvoiceByTenantId(tenant.getId());
        assertThat(after.totalPaid()).isEqualByComparingTo("1080.00");   // reflects the payment
    }

    @Test
    @DisplayName("one org's cached dashboard is never served to another org")
    void cacheIsScopedToTenant() {
        Tenant globex = fixtures.tenant("Globex Bhd", "globex");
        User globexUser = fixtures.user("acc@globex.test", globex,
                fixtures.role("GLOBEX_ACC", "report:read"));

        DashboardStats acme = reportService.dashboard(accountant);      // cached under acme's id
        DashboardStats globexStats = reportService.dashboard(globexUser); // different key -> own data

        assertThat(acme.invoiceCount()).isEqualTo(1);      // Acme has the seeded invoice
        assertThat(globexStats.invoiceCount()).isZero();   // Globex has none — no cache bleed
    }

    private Invoice getOnlyInvoice() {
        return invoiceRepo.findAllInvoiceByTenantId(tenant.getId()).get(0);
    }
}
