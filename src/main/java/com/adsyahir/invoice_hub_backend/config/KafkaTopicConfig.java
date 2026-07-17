package com.adsyahir.invoice_hub_backend.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Declares the topics explicitly rather than relying on the broker's auto-create
 * (which docker-compose disables). Auto-create hides typos: a misspelled topic name
 * silently becomes a real, empty topic that nothing ever publishes to.
 *
 * <p>Spring's KafkaAdmin creates any NewTopic bean at startup, and is idempotent —
 * an existing topic is left alone.
 *
 * <p>Partitions are keyed by tenant id at the publish site. Kafka only guarantees
 * ordering WITHIN a partition, so keying by tenant means one organization's events stay
 * ordered relative to each other while different organizations process in parallel.
 */
@Configuration
public class KafkaTopicConfig {

    /** Facts: something already happened to an invoice. */
    public static final String INVOICE_EVENTS = "invoicehub.invoice-events";

    /** Facts: a payment was recorded. */
    public static final String PAYMENT_EVENTS = "invoicehub.payment-events";

    /** Commands: asks for work to be done (submit this invoice to LHDN). */
    public static final String EINVOICE_COMMANDS = "invoicehub.einvoice-commands";

    /** Commands: refresh the Elasticsearch document for one invoice/client. */
    public static final String SEARCH_INDEX = "invoicehub.search-index";

    @Bean
    NewTopic invoiceEvents() {
        return TopicBuilder.name(INVOICE_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic paymentEvents() {
        return TopicBuilder.name(PAYMENT_EVENTS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic eInvoiceCommands() {
        return TopicBuilder.name(EINVOICE_COMMANDS).partitions(3).replicas(1).build();
    }

    @Bean
    NewTopic searchIndex() {
        return TopicBuilder.name(SEARCH_INDEX).partitions(3).replicas(1).build();
    }

    /**
     * A KafkaTemplate typed &lt;String, Object&gt;: String keys (the tenant id) and any event
     * record as the value.
     *
     * <p>Built explicitly rather than injected. Spring Boot autoconfigures both the
     * ProducerFactory and the KafkaTemplate as {@code <?, ?>}, and Spring matches beans on
     * their full generic type — so asking for a {@code KafkaTemplate<String, Object>} (or a
     * {@code ProducerFactory<String, Object>}) fails with NoSuchBeanDefinitionException. Taking
     * KafkaProperties and constructing the factory sidesteps the wildcard entirely, and still
     * honours every spring.kafka.* setting from application.yml.
     */
    @Bean
    KafkaTemplate<String, Object> eventKafkaTemplate(KafkaProperties properties) {
        Map<String, Object> config = properties.buildProducerProperties();
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
