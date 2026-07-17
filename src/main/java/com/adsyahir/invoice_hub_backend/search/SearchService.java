package com.adsyahir.invoice_hub_backend.search;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import com.adsyahir.invoice_hub_backend.dto.response.GlobalSearchResponse;
import com.adsyahir.invoice_hub_backend.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * The read side of search: one typeahead query across the invoice and client indices.
 *
 * <p>Two guarantees every query here upholds:
 * <ul>
 *   <li><b>Tenant isolation.</b> The tenant id is a bool FILTER on every query — the same
 *       non-negotiable WHERE clause as the JPA repos. A filter (not a must) also skips
 *       scoring: it's a hard yes/no, and ES caches it.</li>
 *   <li><b>Prefix matching.</b> multi_match type=bool_prefix against Search_As_You_Type
 *       fields and their ._2gram/._3gram subfields: every term must match, the last term
 *       may be a prefix — exactly the "user is still typing" behaviour.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    /** Topbar shows a short list per section; "see all" flows go to the list pages. */
    private static final int MAX_HITS_PER_TYPE = 5;

    private static final List<String> INVOICE_FIELDS = List.of(
            "invoiceNumber", "invoiceNumber._2gram", "invoiceNumber._3gram",
            "clientName", "clientName._2gram", "clientName._3gram",
            "notes", "lineItems");

    private static final List<String> CLIENT_FIELDS = List.of(
            "name", "name._2gram", "name._3gram",
            "email", "email._2gram", "email._3gram",
            "phone", "taxId");

    private final ElasticsearchOperations operations;

    /**
     * @param canReadInvoices caller holds invoice:read — otherwise that section is empty
     * @param canReadClients  caller holds client:read — likewise
     */
    public GlobalSearchResponse search(String q, User currentUser,
                                       boolean canReadInvoices, boolean canReadClients) {
        // Super-admin has no tenant scope; there is nothing tenant-safe to search.
        if (currentUser.getTenant() == null || q == null || q.isBlank()) {
            return GlobalSearchResponse.empty();
        }
        Long tenantId = currentUser.getTenant().getId();
        String term = q.trim();

        List<GlobalSearchResponse.InvoiceHit> invoices = canReadInvoices
                ? searchIndex(term, tenantId, INVOICE_FIELDS, InvoiceDocument.class).stream()
                        .map(d -> new GlobalSearchResponse.InvoiceHit(
                                d.getUuid(), d.getInvoiceNumber(), d.getClientName(),
                                d.getStatus(), d.getCurrency(), d.getTotalAmount()))
                        .toList()
                : List.of();

        List<GlobalSearchResponse.ClientHit> clients = canReadClients
                ? searchIndex(term, tenantId, CLIENT_FIELDS, ClientDocument.class).stream()
                        .map(d -> new GlobalSearchResponse.ClientHit(
                                d.getUuid(), d.getName(), d.getEmail()))
                        .toList()
                : List.of();

        return new GlobalSearchResponse(invoices, clients);
    }

    private <T> List<T> searchIndex(String term, Long tenantId, List<String> fields, Class<T> type) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(qb -> qb.bool(b -> b
                        .must(m -> m.multiMatch(mm -> mm
                                .query(term)
                                .type(TextQueryType.BoolPrefix)
                                .fields(fields)))
                        .filter(f -> f.term(t -> t.field("tenantId").value(tenantId)))))
                .withMaxResults(MAX_HITS_PER_TYPE)
                .build();

        return operations.search(query, type).stream()
                .map(SearchHit::getContent)
                .toList();
    }
}
