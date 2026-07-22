package com.adsyahir.invoice_hub_backend.myinvois;

import com.adsyahir.invoice_hub_backend.model.Invoice;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * The single outbound seam to LHDN MyInvois. Every submission goes through {@link #submit(Invoice)},
 * which is wrapped in a Resilience4j circuit breaker.
 *
 * <p><b>Why a breaker here.</b> A tax authority's API is exactly the dependency that has brief
 * outages. Without a breaker, every submission during an outage would wait out the full HTTP
 * timeout before failing — tying up consumer threads and hammering an already-struggling service.
 * The breaker watches the recent failure rate; once it trips OPEN it fails calls instantly for a
 * cool-down window, then probes with a few calls (HALF_OPEN) before closing again. Fast failure,
 * no thundering herd.
 *
 * <p><b>How it composes with Kafka.</b> This method is called from {@code EInvoiceConsumer}, itself
 * a {@code @RetryableTopic} listener. The breaker handles the <em>fast-fail</em> ("don't even try,
 * the dependency is down"); Kafka handles the <em>retry-over-time</em> ("try again in 5s, 10s,
 * 20s…"). A failure — real or short-circuited — surfaces as {@link MyInvoisUnavailableException},
 * which bubbles out of the consumer so Kafka reschedules the work.
 *
 * <p><b>Simulated.</b> The LHDN round-trip below is still faked (see {@code EInvoiceConsumer}'s
 * TODO). {@code app.myinvois.simulate-outage=true} makes every call fail, so the breaker can be
 * demonstrated end to end; tests flip it at runtime via {@link #setForceOutage(boolean)}.
 */
@Component
public class MyInvoisClient {

    private static final Logger log = LoggerFactory.getLogger(MyInvoisClient.class);

    /** Config-driven outage switch, for demoing the breaker without touching code. */
    private final boolean simulateOutage;

    /** Test-only runtime override of the outage switch. */
    private volatile boolean forceOutage;

    public MyInvoisClient(@Value("${app.myinvois.simulate-outage:false}") boolean simulateOutage) {
        this.simulateOutage = simulateOutage;
    }

    /**
     * Submit an invoice to MyInvois and return LHDN's identifiers for the validated document.
     *
     * @throws MyInvoisUnavailableException if the API is unreachable or the breaker is open
     */
    @CircuitBreaker(name = "myinvois", fallbackMethod = "submitFallback")
    public MyInvoisResult submit(Invoice invoice) {
        // --- SIMULATED LHDN round-trip (see EInvoiceConsumer TODO for the real steps) ---
        if (simulateOutage || forceOutage) {
            throw new MyInvoisUnavailableException(
                    "MyInvois API unreachable (simulated outage) for " + invoice.getInvoiceNumber());
        }

        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        String longId = uuid + invoice.getInvoiceNumber().replaceAll("\\D", "");
        return new MyInvoisResult(uuid, longId);
    }

    /**
     * Invoked by Resilience4j when {@link #submit} fails <em>or</em> the breaker is open (it passes
     * a {@code CallNotPermittedException}). We never fabricate a successful result — a tax document
     * must reflect a real submission — so we re-raise a single, uniform transient error and let the
     * consumer's Kafka retry take over.
     */
    @SuppressWarnings("unused") // referenced by name in @CircuitBreaker(fallbackMethod = ...)
    private MyInvoisResult submitFallback(Invoice invoice, Throwable t) {
        log.warn("MyInvois submit unavailable for {} — {}", invoice.getInvoiceNumber(), t.toString());
        if (t instanceof MyInvoisUnavailableException e) {
            throw e;
        }
        throw new MyInvoisUnavailableException(
                "MyInvois submission unavailable for " + invoice.getInvoiceNumber() + " — will retry", t);
    }

    /** Test hook: force every call to fail so the breaker can be driven OPEN deterministically. */
    void setForceOutage(boolean forceOutage) {
        this.forceOutage = forceOutage;
    }
}
