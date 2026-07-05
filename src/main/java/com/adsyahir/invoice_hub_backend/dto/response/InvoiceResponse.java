package com.adsyahir.invoice_hub_backend.dto.response;

import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * API view of an Invoice. Flattens the client/creator to id + name and nests the
 * line items, so we never leak the tenant or JPA proxy internals to the frontend.
 * Mirrors the frontend Invoice type (camelCase contract).
 */
@Getter
@Builder
public class InvoiceResponse {
    private UUID id;
    private String invoiceNumber;

    // Nested client object: serializes as "client": { id, name, email, ... }.
    private ClientSummary client;

    private Long createdById;
    private String createdByName;

    private InvoiceStatus status;   // serialized as its name, e.g. "DRAFT"
    private String currency;

    private BigDecimal subtotal;
    private BigDecimal taxAmount;
    private BigDecimal discountAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal amountDue;

    private LocalDate issueDate;
    private LocalDate dueDate;

    private String notes;
    private String internalNotes;

    private String paymentLinkToken;
    private LocalDateTime paymentLinkExpiresAt;
    private LocalDateTime sentAt;
    private LocalDateTime paidAt;

    // --- LHDN MyInvois e-invoice (Malaysia) ---
    private EInvoiceStatus einvoiceStatus;   // serialized as its name, e.g. "VALIDATED"
    private String einvoiceType;             // LHDN doc type code
    private String myinvoisUuid;
    private String myinvoisLongId;
    private String einvoiceValidationUrl;
    private LocalDateTime einvoiceSubmittedAt;
    private LocalDateTime einvoiceValidatedAt;
    private String einvoiceRejectionReason;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private List<LineItemResponse> lineItems;

    /** Just enough of the client to render the invoice — nested under "client". */
    @Getter
    @Builder
    public static class ClientSummary {
        private Long id;
        private String name;
        private String email;
        private String phone;
        private String currency;
    }

    /** A single line on the invoice. Amounts are server-computed. */
    @Getter
    @Builder
    public static class LineItemResponse {
        private Long id;
        private String description;
        private Long quantity;
        private BigDecimal unitPrice;
        private BigDecimal taxRate;
        private BigDecimal taxAmount;
        private BigDecimal lineTotal;
        private Integer sortOrder;
    }
}
