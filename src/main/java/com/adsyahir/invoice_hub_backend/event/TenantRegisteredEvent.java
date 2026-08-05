package com.adsyahir.invoice_hub_backend.event;

/**
 * A new organization finished signing up. In-process only — unlike its siblings here it
 * is NOT relayed to Kafka. It exists so provisioning that must not be able to fail
 * signup (the Stripe account) can hang off AFTER_COMMIT.
 *
 * @param tenantId   the newly created tenant
 * @param adminEmail the creator; becomes the Stripe account's contact email
 */
public record TenantRegisteredEvent(Long tenantId, String adminEmail) {
}
