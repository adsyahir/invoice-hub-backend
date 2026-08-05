package com.adsyahir.invoice_hub_backend.dto.request;

import lombok.Data;

/**
 * Where Stripe sends the tenant after (or instead of) the hosted onboarding form.
 * Both optional, both untrusted: the service validates them against
 * {@code app.base-url} so this can't become an open redirect.
 */
@Data
public class OnboardingLinkRequest {
    private String returnUrl;
    private String refreshUrl;
}
