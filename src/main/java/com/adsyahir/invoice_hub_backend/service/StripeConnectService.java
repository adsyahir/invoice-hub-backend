package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dto.request.OnboardingLinkRequest;
import com.adsyahir.invoice_hub_backend.dto.response.PayoutsAccountResponse;
import com.adsyahir.invoice_hub_backend.enums.PayoutsStatus;
import com.adsyahir.invoice_hub_backend.event.TenantRegisteredEvent;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import com.stripe.StripeClient;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Stripe Connect (Express) onboarding: one connected account per tenant, created at
 * registration and verified through Stripe's hosted form (KYC must be collected by
 * Stripe from the account holder, never by us).
 *
 * <p>Stripe owns the account state — the columns on {@code tenants} are a cache, written
 * only by {@link #sync(Account)}. With no {@code stripe.key} configured every method
 * behaves as "payouts disabled" so the app still runs without Stripe credentials.
 */
@Service
public class StripeConnectService {

    private static final Logger log = LoggerFactory.getLogger(StripeConnectService.class);

    /**
     * How stale a cached account may be before a read re-fetches it. Webhooks can't reach
     * a laptop, so without this local onboarding would poll a row that never changes.
     */
    private static final Duration SYNC_TTL = Duration.ofSeconds(15);

    private final TenantRepo tenantRepo;
    private final StripeClient stripe;
    private final AuditService auditService;

    /** Blank when Stripe isn't configured, which turns the feature off. */
    @Value("${stripe.key:}")
    private String stripeKey;

    /** Where the tenant is sent back to; also the allow-list for client-supplied URLs. */
    @Value("${app.base-url}")
    private String appBaseUrl;

    /** ISO 3166-1 alpha-2 country the connected accounts are created in. */
    @Value("${stripe.connect.country:MY}")
    private String connectCountry;

    /**
     * Which dashboard the tenant gets: EXPRESS (platform-branded, we host the entry point),
     * FULL (their own Stripe account, Standard-equivalent) or NONE (Custom).
     */
    @Value("${stripe.connect.dashboard:EXPRESS}")
    private String dashboard;

    /**
     * Who eats a disputed or fraudulent charge: STRIPE or PLATFORM.
     *
     * <p>Malaysia forces STRIPE. Stripe's MY availability is "Connect where Stripe collects
     * fees and owns loss liability"; the platform-liable model is preview-only and needs an
     * approval from Stripe Support. Setting PLATFORM without that approval is what produced
     * "Platforms in MY cannot create accounts where the platform is loss-liable".
     *
     * <p>Note the coupling to fees below — the two travel together.
     */
    @Value("${stripe.connect.loss-liability:STRIPE}")
    private String lossLiability;

    public StripeConnectService(TenantRepo tenantRepo, StripeClient stripe, AuditService auditService) {
        this.tenantRepo = tenantRepo;
        this.stripe = stripe;
        this.auditService = auditService;
    }

    /** False when no API key is configured, which turns the whole feature off. */
    public boolean isConfigured() {
        return stripeKey != null && !stripeKey.isBlank();
    }

    // -------------------------------------------------------------------------
    // Provisioning
    // -------------------------------------------------------------------------

    /**
     * Creates the tenant's Express account right after registration.
     *
     * <p>AFTER_COMMIT in its own transaction so a Stripe outage can't roll back a valid
     * signup. Failures are logged and dropped — {@link #createOnboardingLink} creates the
     * account lazily, so the tenant recovers on their next click.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onTenantRegistered(TenantRegisteredEvent event) {
        if (!isConfigured()) {
            log.debug("Stripe not configured — skipping connected account for tenant {}", event.tenantId());
            return;
        }
        tenantRepo.findById(event.tenantId()).ifPresent(tenant -> {
            try {
                ensureAccount(tenant, event.adminEmail());
            } catch (RuntimeException ex) {
                log.error("Could not create Stripe account for tenant {} ({}) — will retry on first "
                        + "onboarding-link request: {}", tenant.getId(), tenant.getSlug(), ex.getMessage());
            }
        });
    }

    /** Account id for the tenant, creating it on first call. Never creates a second one. */
    @Transactional
    public String ensureAccount(Tenant tenant, String adminEmail) {
        if (tenant.getStripeAccountId() != null) {
            return tenant.getStripeAccountId();
        }

        // Controller properties rather than the legacy `type`. `type=express` is shorthand
        // for "platform is loss-liable", which Malaysia forbids; spelling the properties out
        // keeps the Express dashboard while leaving liability with Stripe. Passing both
        // `type` and `controller` is an error, so there is no type here.
        AccountCreateParams.Builder params = AccountCreateParams.builder()
                .setController(controllerProperties())
                .setCountry(connectCountry)
                .setEmail(adminEmail)
                .setBusinessProfile(AccountCreateParams.BusinessProfile.builder()
                        .setName(tenant.getName())
                        .build())
                // Maps a webhook back to a tenant, and makes the Stripe dashboard readable.
                .putMetadata("tenant_id", String.valueOf(tenant.getId()))
                .putMetadata("tenant_slug", tenant.getSlug());

        // A full-dashboard (Standard-equivalent) account owns its own capability set and
        // rejects requests for them; every other shape needs them asked for explicitly.
        if (!isFullDashboard()) {
            params.setCapabilities(AccountCreateParams.Capabilities.builder()
                    .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                            .setRequested(true).build())
                    .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true).build())
                    .build());
        }

        AccountCreateParams built = params.build();
        Account account = call(() -> stripe.accounts().create(built), "create connected account");

        tenant.setStripeAccountId(account.getId());
        applyTo(tenant, account);
        tenantRepo.save(tenant);

        auditService.record(tenant, "PAYOUTS", tenant.getId(), "STRIPE_ACCOUNT_CREATED", null,
                "Created Stripe " + account.getType() + " account " + account.getId());
        log.info("Created Stripe {} account {} for tenant {} ({})",
                account.getType(), account.getId(), tenant.getId(), tenant.getSlug());
        return account.getId();
    }

    // -------------------------------------------------------------------------
    // Reads
    // -------------------------------------------------------------------------

    /**
     * Current payouts state, refreshed from Stripe if stale (see {@link #SYNC_TTL}).
     * Stops refreshing once charges are enabled — the terminal state the UI polls for.
     */
    @Transactional
    public PayoutsAccountResponse get(User currentUser) {
        Tenant tenant = requireTenant(currentUser);

        if (tenant.getStripeAccountId() != null && !tenant.isStripeChargesEnabled() && isStale(tenant)) {
            refreshFromStripe(tenant);
        }
        return toResponse(tenant);
    }

    /**
     * Re-read the account and answer "can this tenant take money right now?".
     *
     * <p>Exists for the payer-facing checkout path, which must not refuse a payment on the
     * strength of a stale cache. Our copy of {@code charges_enabled} is only refreshed by
     * the webhook or by an admin opening Settings → Payouts — so a tenant who finished
     * onboarding while nobody was looking (or with no webhook forwarding in dev) would
     * otherwise keep turning payers away.
     */
    @Transactional
    public boolean refreshChargesEnabled(Tenant tenant) {
        if (!isConfigured() || tenant.getStripeAccountId() == null) {
            return false;
        }
        refreshFromStripe(tenant);
        return tenant.isStripeChargesEnabled();
    }

    private boolean isStale(Tenant tenant) {
        LocalDateTime syncedAt = tenant.getStripeSyncedAt();
        return syncedAt == null || syncedAt.isBefore(LocalDateTime.now().minus(SYNC_TTL));
    }

    /** Failures are swallowed: a stale answer beats a 500 on a screen the tenant watches. */
    private void refreshFromStripe(Tenant tenant) {
        try {
            Account account = stripe.accounts().retrieve(tenant.getStripeAccountId());
            applyTo(tenant, account);
            tenantRepo.save(tenant);
        } catch (StripeException ex) {
            log.warn("Could not refresh Stripe account {} for tenant {}: {}",
                    tenant.getStripeAccountId(), tenant.getId(), ex.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Links
    // -------------------------------------------------------------------------

    /**
     * Mints a single-use AccountLink to Stripe's hosted onboarding. Never cached: Stripe
     * expires these in minutes, which is what {@code refreshUrl} is for.
     */
    @Transactional
    public String createOnboardingLink(User currentUser, OnboardingLinkRequest request) {
        Tenant tenant = requireTenant(currentUser);
        requireConfigured();

        // Covers the tenant whose account creation failed during registration.
        String accountId = ensureAccount(tenant, currentUser.getEmail());

        AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                .setAccount(accountId)
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .setReturnUrl(safeUrl(request == null ? null : request.getReturnUrl(),
                        "/onboarding/payouts/return"))
                .setRefreshUrl(safeUrl(request == null ? null : request.getRefreshUrl(),
                        "/onboarding/payouts?refresh=1"))
                .build();

        return call(() -> stripe.accountLinks().create(params), "create account link").getUrl();
    }

    /**
     * Where the tenant manages payouts, bank details and transfers.
     *
     * <p>Express accounts get a one-time login link into the platform-hosted Express
     * dashboard. STANDARD accounts have a real Stripe account of their own and login links
     * are rejected for them, so they just get sent to dashboard.stripe.com to sign in.
     */
    @Transactional(readOnly = true)
    public String createDashboardLink(User currentUser) {
        Tenant tenant = requireTenant(currentUser);
        requireConfigured();

        if (tenant.getStripeAccountId() == null || !tenant.isStripeChargesEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Finish Stripe onboarding before opening the payouts dashboard");
        }
        if (isFullDashboard()) {
            return "https://dashboard.stripe.com/";
        }
        return call(() -> stripe.accounts().loginLinks().create(tenant.getStripeAccountId()),
                "create dashboard login link").getUrl();
    }

    /**
     * How the connected account is governed.
     *
     * <p>Fees follow liability deliberately: whoever owns the losses collects the fees.
     * Stripe-liable means Stripe bills the connected account directly, so the platform
     * cannot take an application fee — in Malaysia that combination is the only generally
     * available one, and cross-border application fees are barred outright.
     */
    private AccountCreateParams.Controller controllerProperties() {
        boolean platformLiable = "PLATFORM".equalsIgnoreCase(lossLiability);

        return AccountCreateParams.Controller.builder()
                .setLosses(AccountCreateParams.Controller.Losses.builder()
                        .setPayments(platformLiable
                                ? AccountCreateParams.Controller.Losses.Payments.APPLICATION
                                : AccountCreateParams.Controller.Losses.Payments.STRIPE)
                        .build())
                .setFees(AccountCreateParams.Controller.Fees.builder()
                        .setPayer(platformLiable
                                ? AccountCreateParams.Controller.Fees.Payer.APPLICATION
                                : AccountCreateParams.Controller.Fees.Payer.ACCOUNT)
                        .build())
                // Stripe collects the KYC itself via the hosted form — the whole point of
                // the redirect, and the only lawful way for us to obtain these details.
                .setRequirementCollection(AccountCreateParams.Controller.RequirementCollection.STRIPE)
                .setStripeDashboard(AccountCreateParams.Controller.StripeDashboard.builder()
                        .setType(dashboardType())
                        .build())
                .build();
    }

    private AccountCreateParams.Controller.StripeDashboard.Type dashboardType() {
        return switch (dashboard == null ? "" : dashboard.toUpperCase(Locale.ROOT)) {
            case "FULL" -> AccountCreateParams.Controller.StripeDashboard.Type.FULL;
            case "NONE" -> AccountCreateParams.Controller.StripeDashboard.Type.NONE;
            default -> AccountCreateParams.Controller.StripeDashboard.Type.EXPRESS;
        };
    }

    private boolean isFullDashboard() {
        return dashboardType() == AccountCreateParams.Controller.StripeDashboard.Type.FULL;
    }

    /**
     * Validates a client-supplied redirect against {@code app.base-url}, falling back when
     * it doesn't match. Without this the endpoint is an open redirect with Stripe's
     * credibility attached.
     */
    private String safeUrl(String candidate, String fallbackPath) {
        String base = appBaseUrl.endsWith("/")
                ? appBaseUrl.substring(0, appBaseUrl.length() - 1)
                : appBaseUrl;

        if (candidate != null && !candidate.isBlank() && candidate.startsWith(base + "/")) {
            return candidate;
        }
        if (candidate != null && !candidate.isBlank()) {
            log.warn("Rejected off-site Stripe redirect URL '{}' — falling back to {}", candidate, base);
        }
        return base + fallbackPath;
    }

    // -------------------------------------------------------------------------
    // Webhook
    // -------------------------------------------------------------------------

    /**
     * Applies an {@code account.updated} payload — this, not the browser redirect, is what
     * flips a tenant to ENABLED. Verification can finish (or be revoked) long after the
     * form closes. Idempotent: it copies state rather than applying a delta.
     */
    @Transactional
    public void sync(Account account) {
        tenantRepo.findByStripeAccountId(account.getId()).ifPresentOrElse(tenant -> {
            boolean wasEnabled = tenant.isStripeChargesEnabled();
            applyTo(tenant, account);
            tenantRepo.save(tenant);

            if (!wasEnabled && tenant.isStripeChargesEnabled()) {
                auditService.record(tenant, "PAYOUTS", tenant.getId(), "STRIPE_CHARGES_ENABLED", null,
                        "Stripe verified " + account.getId() + " — the tenant can now accept payments");
                log.info("Stripe account {} enabled for tenant {} ({})",
                        account.getId(), tenant.getId(), tenant.getSlug());
            }
        }, () -> log.warn("account.updated for unknown Stripe account {} — ignoring", account.getId()));
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

    /** Copies Stripe's view onto the tenant. The only writer of these flags. */
    private void applyTo(Tenant tenant, Account account) {
        tenant.setStripeChargesEnabled(Boolean.TRUE.equals(account.getChargesEnabled()));
        tenant.setStripePayoutsEnabled(Boolean.TRUE.equals(account.getPayoutsEnabled()));
        tenant.setStripeDetailsSubmitted(Boolean.TRUE.equals(account.getDetailsSubmitted()));
        tenant.setStripeSyncedAt(LocalDateTime.now());

        Account.Requirements requirements = account.getRequirements();
        if (requirements == null) {
            tenant.setStripeRequirements(null);
            tenant.setStripeDisabledReason(null);
            return;
        }
        List<String> due = requirements.getCurrentlyDue();
        tenant.setStripeRequirements(due == null || due.isEmpty() ? null : String.join(",", due));
        tenant.setStripeDisabledReason(requirements.getDisabledReason());
    }

    private PayoutsAccountResponse toResponse(Tenant tenant) {
        return new PayoutsAccountResponse(
                statusOf(tenant),
                tenant.getStripeAccountId(),
                tenant.isStripeChargesEnabled(),
                tenant.isStripePayoutsEnabled(),
                tenant.isStripeDetailsSubmitted(),
                humanizeRequirements(tenant.getStripeRequirements()),
                tenant.getStripeDisabledReason());
    }

    /**
     * Order matters, and both of the first two checks are load-bearing:
     *
     * <p>charges_enabled wins outright — a live account can still have future requirements
     * pending, and calling that tenant "restricted" would be wrong.
     *
     * <p>Then "hasn't onboarded" beats disabled_reason, because Stripe stamps a brand-new
     * account with {@code requirements.past_due} the moment it is created. Checking
     * disabled_reason first labelled every tenant RESTRICTED before they had done
     * anything, which reads as "Stripe rejected you" rather than "please start".
     */
    private PayoutsStatus statusOf(Tenant tenant) {
        if (tenant.isStripeChargesEnabled()) return PayoutsStatus.ENABLED;
        if (!tenant.isStripeDetailsSubmitted()) return PayoutsStatus.NOT_STARTED;
        if (tenant.getStripeDisabledReason() != null) return PayoutsStatus.RESTRICTED;
        return PayoutsStatus.IN_PROGRESS;
    }

    /**
     * Turns Stripe's requirement keys into something a business owner can act on:
     * {@code individual.verification.document} → "Verification document". Best-effort —
     * unknown keys get prettified rather than dropped.
     */
    private List<String> humanizeRequirements(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(this::humanize)
                .distinct()
                .toList();
    }

    private String humanize(String key) {
        return switch (key) {
            case "external_account" -> "Bank account for payouts";
            case "business_profile.url" -> "Business website";
            case "business_profile.mcc" -> "Business category";
            case "tos_acceptance.date", "tos_acceptance.ip" -> "Accept Stripe's terms of service";
            default -> {
                String[] parts = key.split("\\.");
                String last = parts[parts.length - 1].replace('_', ' ');
                yield last.substring(0, 1).toUpperCase(Locale.ROOT) + last.substring(1);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Guards
    // -------------------------------------------------------------------------

    private Tenant requireTenant(User currentUser) {
        if (currentUser == null || currentUser.getTenant() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tenant context");
        }
        return currentUser.getTenant();
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payouts are not enabled on this deployment");
        }
    }

    /** Stripe's checked exception becomes a 502 — it's an upstream failure, not ours. */
    private <T> T call(StripeCall<T> call, String what) {
        try {
            return call.execute();
        } catch (StripeException ex) {
            log.error("Stripe call failed ({}): {}", what, ex.getMessage(), ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not reach Stripe. Please try again.");
        }
    }

    @FunctionalInterface
    private interface StripeCall<T> {
        T execute() throws StripeException;
    }
}
