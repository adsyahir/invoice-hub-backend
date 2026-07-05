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

    @Column(name = "default_currency", nullable = false)
    private String defaultCurrency = "MYR";

    @Column(name = "tax_id", nullable = true)
    private String taxId;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers = 3;

    @Column(name = "max_invoices_per_month", nullable = false)
    private Integer maxInvoicesPerMonth = 10;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
