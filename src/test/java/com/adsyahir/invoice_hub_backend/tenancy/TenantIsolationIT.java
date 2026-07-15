package com.adsyahir.invoice_hub_backend.tenancy;

import com.adsyahir.invoice_hub_backend.dto.response.InvoiceResponse;
import com.adsyahir.invoice_hub_backend.dto.response.PaymentResponse;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.service.NotificationService;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * Multi-tenant isolation. Two organizations exist; each has an invoice. Acme's
 * accountant must not be able to reach, mutate, or even confirm the existence of
 * Globex's data by guessing a uuid.
 *
 * <p>These are security tests, not feature tests. The expected status is
 * <b>404, never 403</b>: a 403 would confirm the record exists and leak that
 * Globex is a customer. Every read goes through a {@code findByUuidAndTenantId}
 * query, so the tenant id is part of the WHERE clause rather than a check applied
 * after loading the row.
 */
class TenantIsolationIT extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private NotificationService notificationService;

    private User acmeAccountant;
    private User globexAccountant;
    private Invoice globexInvoice;

    @BeforeEach
    void seedTwoTenants() {
        // One role row, shared: RBAC grants the same authorities in both orgs. The
        // tenant boundary — not the role — is what must keep these two apart.
        var accountantRole = fixtures.role("ACCOUNTANT",
                "invoice:read", "invoice:write", "invoice:void",
                "payment:read", "payment:record", "payment:refund");

        Tenant acme = fixtures.tenant("Acme Sdn Bhd", "acme");
        acmeAccountant = fixtures.user("accountant@acme.test", acme, accountantRole);
        Client acmeClient = fixtures.client(acme, "Nexus Digital", "billing@nexus.test");
        invoiceService.createInvoice(fixtures.invoiceRequest(acmeClient, "ACME-001"), acmeAccountant);

        Tenant globex = fixtures.tenant("Globex Bhd", "globex");
        globexAccountant = fixtures.user("accountant@globex.test", globex, accountantRole);
        Client globexClient = fixtures.client(globex, "Initech", "ap@initech.test");
        globexInvoice = invoiceService.createInvoice(
                fixtures.invoiceRequest(globexClient, "GLOBEX-001"), globexAccountant);
    }

    private static void assertNotFound(ResponseStatusException error) {
        assertThat(error)
                .as("cross-tenant access must 404, not 403 — a 403 confirms the record exists")
                .isNotNull();
        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("listing invoices only ever returns your own organization's rows")
    void listIsScopedToTheCallersTenant() {
        assertThat(invoiceService.list(acmeAccountant))
                .extracting(InvoiceResponse::getInvoiceNumber)
                .containsExactly("ACME-001");

        assertThat(invoiceService.list(globexAccountant))
                .extracting(InvoiceResponse::getInvoiceNumber)
                .containsExactly("GLOBEX-001");
    }

    @Test
    @DisplayName("fetching another tenant's invoice by uuid is a 404")
    void cannotReadAnotherTenantsInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.show(globexInvoice.getUuid(), acmeAccountant)));
    }

    @Test
    @DisplayName("sending another tenant's invoice is a 404")
    void cannotSendAnotherTenantsInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.send(globexInvoice.getUuid(), acmeAccountant)));
    }

    @Test
    @DisplayName("voiding another tenant's invoice is a 404 and leaves it untouched")
    void cannotVoidAnotherTenantsInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.voidInvoice(globexInvoice.getUuid(), acmeAccountant)));

        assertThat(invoiceService.show(globexInvoice.getUuid(), globexAccountant).getStatus())
                .isEqualTo(com.adsyahir.invoice_hub_backend.enums.InvoiceStatus.DRAFT);
    }

    @Test
    @DisplayName("duplicating another tenant's invoice is a 404 — it would otherwise clone their data into your org")
    void cannotDuplicateAnotherTenantsInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.duplicate(globexInvoice.getUuid(), acmeAccountant)));

        assertThat(invoiceService.list(acmeAccountant)).hasSize(1);
    }

    @Test
    @DisplayName("downloading another tenant's invoice PDF is a 404")
    void cannotDownloadAnotherTenantsPdf() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.pdf(globexInvoice.getUuid(), acmeAccountant)));
    }

    @Test
    @DisplayName("reading another tenant's audit trail is a 404")
    void cannotReadAnotherTenantsAuditTrail() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.auditTrail(globexInvoice.getUuid(), acmeAccountant)));
    }

    @Test
    @DisplayName("submitting another tenant's invoice to MyInvois is a 404 — it would file under the wrong TIN")
    void cannotSubmitAnotherTenantsEInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> invoiceService.submitEInvoice(globexInvoice.getUuid(), acmeAccountant)));
    }

    @Test
    @DisplayName("recording a payment against another tenant's invoice is a 404")
    void cannotPayAnotherTenantsInvoice() {
        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> paymentService.create(
                        fixtures.paymentRequest(globexInvoice.getUuid(), "100.00"), acmeAccountant)));
    }

    @Test
    @DisplayName("refunding another tenant's payment is a 404")
    void cannotRefundAnotherTenantsPayment() {
        invoiceService.send(globexInvoice.getUuid(), globexAccountant);
        PaymentResponse globexPayment = paymentService.create(
                fixtures.paymentRequest(globexInvoice.getUuid(), "500.00"), globexAccountant);

        assertNotFound(catchThrowableOfType(ResponseStatusException.class,
                () -> paymentService.refund(globexPayment.getId(), acmeAccountant)));

        assertThat(paymentService.list(globexAccountant))
                .singleElement()
                .satisfies(p -> assertThat(p.getStatus())
                        .isEqualTo(com.adsyahir.invoice_hub_backend.enums.PaymentStatus.COMPLETED));
    }

    @Test
    @DisplayName("the payments list is scoped to the caller's tenant")
    void paymentListIsScopedToTheCallersTenant() {
        invoiceService.send(globexInvoice.getUuid(), globexAccountant);
        paymentService.create(fixtures.paymentRequest(globexInvoice.getUuid(), "500.00"), globexAccountant);

        assertThat(paymentService.list(acmeAccountant)).isEmpty();
        assertThat(paymentService.list(globexAccountant)).hasSize(1);
    }

    @Test
    @DisplayName("notifications raised by one org never appear in another org's feed")
    void notificationFeedIsScopedToTheCallersTenant() {
        invoiceService.send(globexInvoice.getUuid(), globexAccountant);

        assertThat(notificationService.feed(globexAccountant).items()).isNotEmpty();
        assertThat(notificationService.feed(acmeAccountant).items()).isEmpty();
        assertThat(notificationService.feed(acmeAccountant).unreadCount()).isZero();
    }
}
