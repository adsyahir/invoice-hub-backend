package com.adsyahir.invoice_hub_backend.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ClientSearchRepo extends ElasticsearchRepository<ClientDocument, Long> {
}
