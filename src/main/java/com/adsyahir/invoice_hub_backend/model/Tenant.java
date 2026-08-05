package com.adsyahir.invoice_hub_backend.model;

import com.adsyahir.invoice_hub_backend.enums.TenantPlan;
import com.adsyahir.invoice_hub_backend.enums.TenantStatus;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Entity
@Table(name = "tenants")
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantPlan plan = TenantPlan.FREE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status = TenantStatus.ACTIVE;

    @Column(name = "billing_email", nullable = true)
    private String billingEmail;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency = "MYR";   // ISO 4217

    @Column(name = "tax_id", nullable = true)
    private String taxId;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 3;

    @Column(name = "max_invoices_per_month", nullable = false)
    private Integer maxInvoicesPerMonth = 10;

    // --- Stripe Connect (Express) ---
    // A cache of the connected account's state, refreshed from the account.updated
    // webhook. Stripe owns these values; nothing here should set chargesEnabled itself.

    @Column(name = "stripe_account_id", unique = true)
    private String stripeAccountId;

    @Column(name = "stripe_charges_enabled", nullable = false)
    private boolean stripeChargesEnabled = false;

    @Column(name = "stripe_payouts_enabled", nullable = false)
    private boolean stripePayoutsEnabled = false;

    @Column(name = "stripe_details_submitted", nullable = false)
    private boolean stripeDetailsSubmitted = false;

    @Column(name = "stripe_disabled_reason")
    private String stripeDisabledReason;

    /** requirements.currently_due, comma-separated. Null when nothing is due. */
    @Column(name = "stripe_requirements", columnDefinition = "text")
    private String stripeRequirements;

    @Column(name = "stripe_synced_at")
    private LocalDateTime stripeSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
