package com.adsyahir.invoice_hub_backend.model;

import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** A payment recorded against an invoice. Many payments -> one invoice. */
@Entity
@Table(name = "payments")
@Getter
@Setter
@Builder
@NoArgsConstructor          // ← required by JPA
@AllArgsConstructor
// Soft delete: delete() flips deleted_at instead of removing the row; all reads
// are filtered to deleted_at IS NULL.
@SQLDelete(sql = "UPDATE payments SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public, unguessable handle exposed in URLs/API. Internal joins use id.
    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Many payments belong to one invoice (partial payments, retries, etc.).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;            // ISO 4217, e.g. "MYR"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(length = 50)
    private String gateway;             // STRIPE, BILLPLZ, MANUAL (nullable)

    @Column(name = "gateway_txn_id")
    private String gatewayTxnId;        // provider transaction id (nullable)

    private String reference;           // manual ref / receipt no. (nullable)

    // Who logged a manual payment (null for gateway/automatic payments).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "paid_at", nullable = false)
    private LocalDateTime paidAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Null = live. Set by the @SQLDelete statement above on soft delete.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
