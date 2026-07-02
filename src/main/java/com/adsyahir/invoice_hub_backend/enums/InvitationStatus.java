package com.adsyahir.invoice_hub_backend.enums;

/** Lifecycle of a team invitation. Stored as a string via @Enumerated(EnumType.STRING). */
public enum InvitationStatus {
    PENDING,    // created, emailed, not yet acted on
    ACCEPTED,   // invitee signed up; a users row now exists
    EXPIRED,    // passed expires_at without acceptance
    REVOKED     // cancelled by an admin before acceptance
}
