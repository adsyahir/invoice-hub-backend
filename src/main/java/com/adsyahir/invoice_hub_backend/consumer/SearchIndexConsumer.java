package com.adsyahir.invoice_hub_backend.consumer;

import com.adsyahir.invoice_hub_backend.config.KafkaTopicConfig;
import com.adsyahir.invoice_hub_backend.event.SearchIndexRequested;
import com.adsyahir.invoice_hub_backend.search.SearchIndexService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.stereotype.Component;

/**
 * Keeps Elasticsearch in step with PostgreSQL, one entity at a time.
 *
 * <p>Why this is a Kafka consumer and not an inline call after each save: the search
 * index is a nice-to-have projection — an ES hiccup must never fail or slow an invoice
 * write. Here a failed index write throws, Kafka retries with backoff, and a persistent
 * failure lands on the DLT to be replayed once ES is back. (And because the DB is the
 * source of truth, even a lost record only costs one stale document until the next
 * write or the startup reindex.)
 *
 * <p>Idempotent by construction: the service reloads the entity and upserts by id, so
 * an at-least-once redelivery just writes the same document again. No eventId table
 * needed here.
 */
@Component
@RequiredArgsConstructor
public class SearchIndexConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexConsumer.class);

    private final SearchIndexService searchIndexService;

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltTopicSuffix = "-dlt")
    @KafkaListener(topics = KafkaTopicConfig.SEARCH_INDEX, groupId = "search-indexer")
    public void onSearchIndexRequested(SearchIndexRequested event) {
        log.debug("Search index refresh: {} {} deleted={} (eventId={})",
                event.entityType(), event.entityId(), event.deleted(), event.eventId());

        switch (event.entityType()) {
            case INVOICE -> searchIndexService.indexInvoice(event.entityId());
            case CLIENT -> {
                if (event.deleted()) {
                    searchIndexService.removeClient(event.entityId());
                } else {
                    searchIndexService.indexClient(event.entityId());
                }
            }
        }
    }

    @DltHandler
    public void dlt(SearchIndexRequested event) {
        // Not fatal to the business — the DB row is fine, only its search document is
        // stale. Replay from the DLT, or just restart the app (startup reindex).
        log.error("Search indexing PERMANENTLY failed after retries — {} {} eventId={}",
                event.entityType(), event.entityId(), event.eventId());
    }
}
