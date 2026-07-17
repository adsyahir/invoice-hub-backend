package com.adsyahir.invoice_hub_backend.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/**
 * Elasticsearch repository — same Spring Data idea as the JPA repos, different store.
 * (With two Spring Data modules on the classpath, Spring assigns each repository by the
 * interface it extends: JpaRepository -> PostgreSQL, ElasticsearchRepository -> ES.)
 */
public interface InvoiceSearchRepo extends ElasticsearchRepository<InvoiceDocument, Long> {

    /** Drops every invoice document of one client — used when the client is deleted. */
    void deleteByClientId(Long clientId);
}
