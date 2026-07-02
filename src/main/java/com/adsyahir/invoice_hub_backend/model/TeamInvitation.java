package com.adsyahir.invoice_hub_backend.model;

import com.adsyahir.invoice_hub_backend.enums.InvitationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A pending invitation for someone to join a tenant's team. Created when an admin
 * invites an email; the {@code token} is embedded in the emailed link and consumed
 * on acceptance (which then creates the {@link User} row).
 */
@Getter
@Setter
@Entity
@Table(name = "team_invitations")
@Builder
@NoArgsConstructor          // ← required by JPA
@AllArgsConstructor
public class TeamInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Public, unguessable handle exposed in URLs/API. Internal joins use id.
    @UuidGenerator
    @Column(nullable = false, unique = true, updatable = false)
    private UUID uuid;

    // The organization the invitee is being invited to (multi-tenant boundary).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    // Invitee's email address (the message recipient).
    @Column(nullable = false)
    private String email;

    // Role granted when the invite is accepted.
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Secret embedded in the invitation link; looked up on acceptance.
    @Column(nullable = false, unique = true)
    private String token;

    // PENDING | ACCEPTED | EXPIRED | REVOKED. Stored as its string name.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvitationStatus status = InvitationStatus.PENDING;

    // The inviter (used for "inviterName" in the email + subject).
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "invited_by", nullable = false)
    private User invitedBy;

    // Absolute expiry (now() + expiryHours). Compared against to decide if a link is stale.
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // Set when the invite is consumed; null while still pending.
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
