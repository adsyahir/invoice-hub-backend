package com.adsyahir.invoice_hub_backend.config;

import com.stripe.StripeClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The platform's Stripe credentials — InvoiceHub's own key (sk_…), shared across all
 * tenants, since a connected account is addressed by its id and never by a key of its
 * own. Comes from STRIPE_API_KEY in .env; never commit it or write it to the database.
 *
 * <p>The default is empty so the app still boots without Stripe credentials (and for the
 * test suite, which never calls Stripe). A blank key reads as "payouts not enabled on
 * this deployment" downstream — see {@code StripeConnectService.isConfigured()}.
 */
@Configuration
public class StripeConfig {

    private final String secretKey;

    public StripeConfig(@Value("${stripe.key:}") String secretKey) {
        this.secretKey = secretKey;
    }

    @Bean
    public StripeClient stripeClient() {
        return new StripeClient(this.secretKey);
    }
}
