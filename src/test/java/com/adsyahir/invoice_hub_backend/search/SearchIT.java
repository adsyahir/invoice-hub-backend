package com.adsyahir.invoice_hub_backend.search;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dto.response.GlobalSearchResponse;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.service.InvoiceService;
import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Elasticsearch search, end to end. The core test drives the REAL pipeline: service
 * write -> Spring event -> Kafka -> SearchIndexConsumer -> Elasticsearch -> bool_prefix
 * query — so it proves the whole chain, not just the query DSL.
 *
 * <p>Everything search-related is asynchronous (Kafka consumer + ES's own ~1s refresh),
 * so positive assertions always go through awaitility. Negative assertions ("tenant B
 * sees nothing") are made AFTER the positive one confirms the document is searchable —
 * asserting "empty" before indexing finished would pass vacuously.
 */
class SearchIT extends AbstractIntegrationTest {

    private static final Duration INDEX_TIMEOUT = Duration.ofSeconds(15);

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private SearchService searchService;

    @Autowired
    private SearchIndexService searchIndexService;

    @Autowired
    private ClientRepo clientRepo;

    private Tenant tenant;
    private User accountant;
    private Client client;

    @BeforeEach
    void seedTenant() {
        tenant = fixtures.tenant("Acme Sdn Bhd", "acme");
        accountant = fixtures.user("accountant@acme.test", tenant,
                fixtures.role("ACCOUNTANT", "invoice:read", "invoice:write",
                        "client:read", "report:read"));
        client = fixtures.client(tenant, "Nexus Digital", "billing@nexus.test");
    }

    private GlobalSearchResponse search(String q, User user) {
        return searchService.search(q, user, true, true);
    }

    @Test
    @DisplayName("a created invoice becomes searchable via the full Kafka pipeline")
    void invoiceIsSearchableAfterCreate() {
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-7701"), accountant);

        // Prefix of the invoice number — the typeahead case.
        await().atMost(INDEX_TIMEOUT).untilAsserted(() ->
                assertThat(search("INV-77", accountant).invoices())
                        .anySatisfy(hit -> {
                            assertThat(hit.invoiceNumber()).isEqualTo("INV-7701");
                            assertThat(hit.clientName()).isEqualTo("Nexus Digital");
                            assertThat(hit.status()).isEqualTo("DRAFT");
                        }));

        // The denormalized client name and the line-item text find the same invoice.
        assertThat(search("Nexus", accountant).invoices())
                .anyMatch(h -> h.invoiceNumber().equals("INV-7701"));
        assertThat(search("Consulting", accountant).invoices())
                .anyMatch(h -> h.invoiceNumber().equals("INV-7701"));
    }

    @Test
    @DisplayName("one org's documents are never returned to another org")
    void searchIsTenantScoped() {
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-7702"), accountant);
        searchIndexService.indexClient(client.getId());

        // Wait until the documents are definitely searchable for their OWN tenant...
        await().atMost(INDEX_TIMEOUT).untilAsserted(() -> {
            assertThat(search("INV-7702", accountant).invoices()).isNotEmpty();
            assertThat(search("Nexus", accountant).clients()).isNotEmpty();
        });

        // ...and only then assert the other tenant sees nothing for the same terms.
        Tenant globex = fixtures.tenant("Globex Bhd", "globex");
        User globexUser = fixtures.user("acc@globex.test", globex,
                fixtures.role("GLOBEX_ACC", "invoice:read", "client:read"));

        GlobalSearchResponse other = search("INV-7702", globexUser);
        assertThat(other.invoices()).isEmpty();
        assertThat(search("Nexus", globexUser).clients()).isEmpty();
    }

    @Test
    @DisplayName("renaming a client refreshes the denormalized name on their invoice documents")
    void clientRenameRefreshesInvoiceDocuments() {
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-7703"), accountant);
        await().atMost(INDEX_TIMEOUT).untilAsserted(() ->
                assertThat(search("Nexus", accountant).invoices()).isNotEmpty());

        // Rename in the DB, then refresh — what the consumer does for a CLIENT event.
        client.setName("Quantum Ventures");
        clientRepo.save(client);
        searchIndexService.indexClient(client.getId());

        await().atMost(INDEX_TIMEOUT).untilAsserted(() -> {
            assertThat(search("Quantum", accountant).invoices())
                    .anyMatch(h -> h.invoiceNumber().equals("INV-7703"));
            assertThat(search("Nexus", accountant).invoices()).isEmpty();   // no stale name
        });
    }

    @Test
    @DisplayName("deleting a client drops their document and all their invoice documents")
    void clientDeleteRemovesDocuments() {
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-7704"), accountant);
        searchIndexService.indexClient(client.getId());
        await().atMost(INDEX_TIMEOUT).untilAsserted(() -> {
            assertThat(search("INV-7704", accountant).invoices()).isNotEmpty();
            assertThat(search("Nexus", accountant).clients()).isNotEmpty();
        });

        searchIndexService.removeClient(client.getId());

        await().atMost(INDEX_TIMEOUT).untilAsserted(() -> {
            assertThat(search("Nexus", accountant).clients()).isEmpty();
            assertThat(search("INV-7704", accountant).invoices()).isEmpty();
        });
    }

    @Test
    @DisplayName("a section the caller lacks permission for comes back empty")
    void permissionGatesEachSection() {
        invoiceService.createInvoice(fixtures.invoiceRequest(client, "INV-7705"), accountant);
        searchIndexService.indexClient(client.getId());
        await().atMost(INDEX_TIMEOUT).untilAsserted(() ->
                assertThat(search("Nexus", accountant).invoices()).isNotEmpty());

        // Same user, but the controller decided they hold only client:read.
        GlobalSearchResponse clientsOnly = searchService.search("Nexus", accountant, false, true);
        assertThat(clientsOnly.invoices()).isEmpty();
        assertThat(clientsOnly.clients()).isNotEmpty();
    }
}
