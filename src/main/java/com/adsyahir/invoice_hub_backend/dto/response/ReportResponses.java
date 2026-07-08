package com.adsyahir.invoice_hub_backend.dto.response;

import java.math.BigDecimal;

/** Report DTOs — flat records mirroring the frontend's dashboard/report types. */
public final class ReportResponses {

    private ReportResponses() {}

    /** Dashboard stat cards (US-030). Change fields are % vs the previous month. */
    public record DashboardStats(
            BigDecimal totalInvoiced,
            BigDecimal totalPaid,
            BigDecimal totalOverdue,
            BigDecimal outstanding,
            String currency,
            long invoiceCount,
            long paidCount,
            long overdueCount,
            BigDecimal invoicedChange,
            BigDecimal paidChange
    ) {}

    /** One month of the revenue chart. */
    public record RevenuePoint(String month, BigDecimal invoiced, BigDecimal paid) {}

    /** One aging bucket (US-032). */
    public record AgingBucket(String bucket, BigDecimal amount, long count) {}
}
