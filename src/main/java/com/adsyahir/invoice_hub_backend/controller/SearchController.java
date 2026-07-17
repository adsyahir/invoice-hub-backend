package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dto.response.GlobalSearchResponse;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.search.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /**
     * Topbar typeahead: GET /api/search?q=inv
     *
     * <p>Requires at least one of the read permissions; each result SECTION is then
     * gated on its own permission, so an accountant without client:read still searches
     * invoices but never sees client hits.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('invoice:read', 'client:read')")
    public ResponseEntity<GlobalSearchResponse> search(@RequestParam("q") String q,
                                                       @AuthenticationPrincipal UserPrincipal principal) {
        boolean canReadInvoices = hasAuthority(principal, "invoice:read");
        boolean canReadClients = hasAuthority(principal, "client:read");
        return ResponseEntity.ok(
                searchService.search(q, principal.getUser(), canReadInvoices, canReadClients));
    }

    private static boolean hasAuthority(UserPrincipal principal, String authority) {
        return principal.getAuthorities().stream()
                .anyMatch(a -> authority.equals(a.getAuthority()));
    }
}
