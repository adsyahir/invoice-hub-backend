package com.adsyahir.invoice_hub_backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Per-tenant MyInvois e-invoicing configuration (table V29). Intermediary model:
 * only the taxpayer's TIN/BRN/SST are stored here. InvoiceHub's own intermediary
 * client credentials live in app config (.env), not the DB, and are shared across
 * all tenants.
 */
@Entity
@Table(name = "tenant_einvoice_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantEinvoiceSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private Tenant tenant;

    @Column(nullable = false, length = 20)
    private String environment = "SANDBOX";     // SANDBOX | PRODUCTION

    @Column(length = 30)
    private String tin;

    @Column(length = 30)
    private String brn;

    @Column(name = "sst_number", length = 30)
    private String sstNumber;

    @Column(nullable = false, length = 20)
    private String status = "NOT_CONNECTED";    // NOT_CONNECTED | CONNECTED | ERROR

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
