package com.adsyahir.invoice_hub_backend.model;


import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Table(name = "users")
@Entity
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public handle exposed in URLs/API (dual-key: id for FKs/joins, uuid for the outside world).
    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // ACTIVE | INVITED | SUSPENDED — plain string for now; promote to a UserStatus enum
    // (like TenantStatus) with @Enumerated(EnumType.STRING) when you need type safety.
    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    // Soft-delete marker: null = active row, non-null = deleted (mirrors clients/invoices).
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // The organization this user belongs to. Nullable: platform SUPER_ADMINs are
    // not scoped to a single tenant (see V3 migration).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RefreshToken> refreshTokens = new ArrayList<>();

    public void setPassword(@Nullable String password) {
        this.password = password;
    }

    public @Nullable CharSequence getPassword() {
        return password;
    }
}
