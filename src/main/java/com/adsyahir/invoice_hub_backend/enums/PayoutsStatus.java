package com.adsyahir.invoice_hub_backend.enums;

/**
 * Where a tenant stands in Stripe Connect onboarding. Not a boolean: the account exists
 * from registration, but charges only switch on after Stripe verifies the KYC details,
 * and the UI needs to tell "waiting on Stripe" apart from "Stripe wants more".
 *
 * <p>Derived from the cached account flags, never stored.
 */
public enum PayoutsStatus {
    /** Account created; the tenant has not submitted Stripe's hosted form yet. */
    NOT_STARTED,
    /** Form submitted (or partially), Stripe still verifying. Charges are off. */
    IN_PROGRESS,
    /** charges_enabled: true. The tenant can accept payments. */
    ENABLED,
    /** Stripe disabled the account and has outstanding requirements. */
    RESTRICTED
}
