package com.adsyahir.invoice_hub_backend.myinvois;

import com.adsyahir.invoice_hub_backend.model.Invoice;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the Resilience4j breaker around the MyInvois call actually trips and short-circuits,
 * verified through the real Spring AOP aspect.
 *
 * <p>Unlike the other {@code *IT}s this does <b>not</b> extend {@code AbstractIntegrationTest}:
 * fault tolerance is pure in-JVM behaviour, so booting Postgres/Kafka/Redis/Elasticsearch
 * containers for it would be wasted time (and, under memory pressure, a flaky one). Instead it
 * loads a minimal context — just {@link MyInvoisClient} plus Resilience4j's circuit-breaker
 * auto-configuration and Spring AOP — and drives the breaker's config from inline properties.
 *
 * <p>The breaker is a shared singleton, so its state is reset around each test.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        classes = {
                MyInvoisClient.class,
                CircuitBreakerAutoConfiguration.class,
                AopAutoConfiguration.class,
        },
        properties = {
                "resilience4j.circuitbreaker.instances.myinvois.sliding-window-type=COUNT_BASED",
                "resilience4j.circuitbreaker.instances.myinvois.sliding-window-size=10",
                "resilience4j.circuitbreaker.instances.myinvois.minimum-number-of-calls=5",
                "resilience4j.circuitbreaker.instances.myinvois.failure-rate-threshold=50",
                "resilience4j.circuitbreaker.instances.myinvois.wait-duration-in-open-state=30s",
                "resilience4j.circuitbreaker.instances.myinvois.permitted-number-of-calls-in-half-open-state=3",
                "resilience4j.circuitbreaker.instances.myinvois.record-exceptions="
                        + "com.adsyahir.invoice_hub_backend.myinvois.MyInvoisUnavailableException",
        })
class EInvoiceCircuitBreakerIT {

    @Autowired
    private MyInvoisClient myInvoisClient;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker breaker() {
        return circuitBreakerRegistry.circuitBreaker("myinvois");
    }

    @BeforeEach
    void resetBreaker() {
        breaker().reset();
        myInvoisClient.setForceOutage(false);
    }

    @AfterEach
    void clearOutage() {
        breaker().reset();
        myInvoisClient.setForceOutage(false);
    }

    private static Invoice invoice(String number) {
        Invoice inv = new Invoice();
        inv.setInvoiceNumber(number);
        return inv;
    }

    @Test
    void breakerOpensAfterRepeatedFailuresAndThenShortCircuits() {
        myInvoisClient.setForceOutage(true);

        // minimum-number-of-calls = 5, failure-rate-threshold = 50%. Five straight failures
        // (100%) must trip the breaker OPEN.
        for (int i = 0; i < 5; i++) {
            final int n = i;
            assertThatThrownBy(() -> myInvoisClient.submit(invoice("INV-CB-" + n)))
                    .isInstanceOf(MyInvoisUnavailableException.class);
        }

        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        long notPermittedBefore = breaker().getMetrics().getNumberOfNotPermittedCalls();

        // With the breaker OPEN the primary method is never entered — the call is short-circuited
        // and routed straight to the fallback, which re-raises the transient error.
        assertThatThrownBy(() -> myInvoisClient.submit(invoice("INV-CB-open")))
                .isInstanceOf(MyInvoisUnavailableException.class);

        assertThat(breaker().getMetrics().getNumberOfNotPermittedCalls())
                .isGreaterThan(notPermittedBefore);
    }

    @Test
    void closedBreakerLetsSuccessfulSubmissionsThrough() {
        // Sanity check the happy path still flows while the breaker is CLOSED.
        MyInvoisResult result = myInvoisClient.submit(invoice("INV-OK-1"));

        assertThat(result.uuid()).isNotBlank();
        assertThat(result.longId()).contains("1");
        assertThat(breaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
