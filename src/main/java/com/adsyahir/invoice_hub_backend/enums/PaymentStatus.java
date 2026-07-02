package com.adsyahir.invoice_hub_backend.enums;

/** Lifecycle of a payment. Stored as its name (EnumType.STRING). */
public enum PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}
