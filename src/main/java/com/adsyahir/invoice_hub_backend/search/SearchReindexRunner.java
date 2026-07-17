package com.adsyahir.invoice_hub_backend.search;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Rebuilds both search indices from PostgreSQL at startup. This is the safety net that
 * lets the index be truly disposable: a wiped ES volume, a mapping change, or events
 * missed while ES was down all self-heal on the next boot.
 *
 * <p>Fine while the dataset is small (it is). At real scale this becomes an admin
 * endpoint / batch job instead of a boot step — flip the property off and trigger
 * reindexAll() manually.
 *
 * <p>Failure here must NOT abort startup: search is a projection, invoicing works
 * without it. Log loudly and move on.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.search.reindex-on-startup", havingValue = "true", matchIfMissing = true)
public class SearchReindexRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SearchReindexRunner.class);

    private final SearchIndexService searchIndexService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            searchIndexService.reindexAll();
        } catch (Exception e) {
            log.error("Startup search reindex failed — search results may be stale or empty "
                    + "until Elasticsearch is reachable: {}", e.getMessage(), e);
        }
    }
}
