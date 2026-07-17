package com.adsyahir.invoice_hub_backend.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Topbar search results, grouped by type. A section the user lacks permission for
 * (or that simply has no matches) is an empty list, never null.
 */
public record GlobalSearchResponse(
        List<InvoiceHit> invoices,
        List<ClientHit> clients
) {
    public record InvoiceHit(
            String id,             // public uuid — what the frontend navigates with
            String invoiceNumber,
            String clientName,
            String status,
            String currency,
            BigDecimal totalAmount
    ) {}

    public record ClientHit(
            String id,             // public uuid
            String name,
            String email
    ) {}

    public static GlobalSearchResponse empty() {
        return new GlobalSearchResponse(List.of(), List.of());
    }
}
