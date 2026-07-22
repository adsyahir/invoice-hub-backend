package com.adsyahir.invoice_hub_backend.myinvois;

/**
 * The MyInvois (LHDN) submission could not be completed right now — the API is unreachable,
 * erroring, or the circuit breaker is open and short-circuiting the call.
 *
 * <p>This is a <em>transient</em> failure by design: it signals "try again later", not "this
 * invoice is invalid". The e-invoice consumer lets it propagate so Kafka's {@code @RetryableTopic}
 * reschedules the submission. A tax document is never faked in its place.
 */
public class MyInvoisUnavailableException extends RuntimeException {

    public MyInvoisUnavailableException(String message) {
        super(message);
    }

    public MyInvoisUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
