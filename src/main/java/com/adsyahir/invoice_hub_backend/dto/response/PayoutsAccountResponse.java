package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.PayoutsStatus;

import java.util.List;

/**
 * The tenant's Stripe Connect state, for Settings → Payouts and the onboarding flow.
 * Tenant-owned facts only — the account id is useless without our API key, and no
 * platform credential appears here.
 */
public record PayoutsAccountResponse(
        PayoutsStatus status,
        String stripeAccountId,
        boolean chargesEnabled,
        boolean payoutsEnabled,
        boolean detailsSubmitted,
        /** requirements.currently_due, in plain English. Empty when nothing is due. */
        List<String> requirements,
        String disabledReason) {
}
