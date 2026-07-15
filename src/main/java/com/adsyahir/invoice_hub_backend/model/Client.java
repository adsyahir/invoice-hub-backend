package com.adsyahir.invoice_hub_backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/** A customer a tenant bills. Tenant-scoped; address geo via FK lookups. */
@Getter
@Setter
@Entity
@Data
@Table(name = "clients")
@Builder
@NoArgsConstructor          // ← required by JPA
@AllArgsConstructor
// Soft delete: deleteById()/delete() flip deleted_at instead of removing the row,
// and every query is silently filtered to rows where deleted_at IS NULL.
@SQLDelete(sql = "UPDATE clients SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public, unguessable handle exposed in URLs/API. Internal joins use id.
    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    // The organization that owns this client (multi-tenant boundary).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "address_line1")
    private String addressLine1;

    private String country;

    // @Builder.Default is required on every initialized field: without it,
    // Client.builder() ignores the initializer and the column comes out NULL,
    // which the NOT NULL constraint then rejects at insert time.
    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "MYR";

    @Builder.Default
    @Column(name = "payment_terms_days", nullable = false)
    private Integer paymentTermsDays = 30;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "state_id")
    private State state;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "postcode_id")
    private Postcode postcode;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    // Null = live. Set by the @SQLDelete statement above on soft delete.
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
