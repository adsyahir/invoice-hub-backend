package com.adsyahir.invoice_hub_backend.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Seeds Malaysian geo reference data (states → cities → postcodes) from
 * {@code resources/data/my-geo.json}. Manual only (--seed flag) and
 * idempotent: skips entirely once states are present, and every insert is an
 * upsert/ON CONFLICT so a partial run can be safely re-run.
 */
@Component
@Order(3)
public class GeoSeeder implements CommandLineRunner {

    private final JdbcTemplate jdbc;
    // Local instance — avoids depending on an autoconfigured ObjectMapper bean,
    // which isn't present here. It's only used for this one-off seed parse.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeoSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!Arrays.asList(args).contains("--seed")) {
            return;
        }

        // Already loaded? The dataset is large — don't re-read it every --seed.
        Integer states = jdbc.queryForObject("SELECT COUNT(*) FROM states", Integer.class);
        if (states != null && states > 0) {
            return;
        }

        JsonNode root;
        try (InputStream in = new ClassPathResource("data/my-geo.json").getInputStream()) {
            root = objectMapper.readTree(in);
        }

        for (JsonNode state : root.path("state")) {
            Long stateId = jdbc.queryForObject(
                    "INSERT INTO states(name) VALUES (?) " +
                            "ON CONFLICT (name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                    Long.class, state.path("name").asText());

            for (JsonNode city : state.path("city")) {
                Long cityId = jdbc.queryForObject(
                        "INSERT INTO cities(state_id, name) VALUES (?, ?) " +
                                "ON CONFLICT (state_id, name) DO UPDATE SET name = EXCLUDED.name RETURNING id",
                        Long.class, stateId, city.path("name").asText());

                List<Object[]> batch = new ArrayList<>();
                for (JsonNode postcode : city.path("postcode")) {
                    batch.add(new Object[]{cityId, postcode.asText()});
                }
                jdbc.batchUpdate(
                        "INSERT INTO postcodes(city_id, code) VALUES (?, ?) " +
                                "ON CONFLICT (city_id, code) DO NOTHING",
                        batch);
            }
        }
    }
}
