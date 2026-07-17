package com.adsyahir.invoice_hub_backend.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The SEARCH projection of an invoice — what Elasticsearch stores, not what PostgreSQL
 * stores. The database stays the source of truth; this document is a denormalized,
 * disposable copy that can be rebuilt from it at any time (see SearchReindexRunner).
 *
 * <p>Denormalized on purpose: {@code clientName} is copied in so a search for "Nexus"
 * finds that client's invoices without a join — Elasticsearch has no joins. The cost is
 * that renaming a client must reindex their invoices (SearchIndexService handles it).
 *
 * <p>{@code Search_As_You_Type} builds edge n-gram subfields ({@code ._2gram},
 * {@code ._3gram}) at index time, so a prefix like "INV-20" matches instantly with a
 * {@code bool_prefix} multi_match — this is what makes the topbar typeahead work.
 *
 * <p>NEVER indexed: paymentLinkToken (a bearer credential — anyone holding it can view
 * and pay the invoice) and internalNotes (staff-only text has no business in a search
 * index that could one day back a less-trusted UI).
 */
@Document(indexName = "invoices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDocument {

    /** Same id as the database row, so index/delete are natural upserts by id. */
    @Id
    private Long id;

    /** The multi-tenant boundary. EVERY query must filter on this — see SearchService. */
    @Field(type = FieldType.Long)
    private Long tenantId;

    /** Lets a client deletion drop all of their invoice documents in one query. */
    @Field(type = FieldType.Long)
    private Long clientId;

    /** Public handle the frontend navigates with (/invoices/{uuid}). */
    @Field(type = FieldType.Keyword)
    private String uuid;

    @Field(type = FieldType.Search_As_You_Type)
    private String invoiceNumber;

    @Field(type = FieldType.Search_As_You_Type)
    private String clientName;

    @Field(type = FieldType.Keyword)
    private String status;

    @Field(type = FieldType.Keyword)
    private String currency;

    @Field(type = FieldType.Double)
    private BigDecimal totalAmount;

    @Field(type = FieldType.Text)
    private String notes;

    /** Line-item descriptions, so "logo design" finds the invoice that billed it. */
    @Field(type = FieldType.Text)
    private List<String> lineItems;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate issueDate;

    @Field(type = FieldType.Date, format = DateFormat.date)
    private LocalDate dueDate;
}
