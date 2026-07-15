package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.PaymentRepo;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.AgingBucket;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.DashboardStats;
import com.adsyahir.invoice_hub_backend.dto.response.ReportResponses.RevenuePoint;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.Payment;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Read-only reporting aggregates for the dashboard and reports screens, computed
 * on the fly from the tenant's invoices and payments. All figures are
 * tenant-scoped via the authenticated user.
 *
 * (This is exactly the workload Tier-2 Redis caching will target later — the
 * dashboard call recomputes every time today.)
 */
@Service
public class ReportService {

    private static final int REVENUE_MONTHS = 6;

    private final InvoiceRepo invoiceRepo;
    private final PaymentRepo paymentRepo;

    public ReportService(InvoiceRepo invoiceRepo, PaymentRepo paymentRepo) {
        this.invoiceRepo = invoiceRepo;
        this.paymentRepo = paymentRepo;
    }

    // Keyed on tenant id so everyone in the org shares one cached result (keying on the user
    // would cache identical numbers once per person). condition skips caching when there is no
    // tenant — a platform SUPER_ADMIN — because #currentUser.tenant.id would NPE, and its
    // result is the empty-stats fallback anyway. Evicted by ReportCacheEvictor on every write.
    @Cacheable(value = "dashboard", key = "#currentUser.tenant.id",
            condition = "#currentUser.tenant != null")
    @Transactional(readOnly = true)
    public DashboardStats dashboard(User currentUser) {
        String currency = currencyOf(currentUser);
        if (currentUser.getTenant() == null) {
            return new DashboardStats(z(), z(), z(), z(), currency, 0, 0, 0, z(), z());
        }
        List<Invoice> invoices = invoiceRepo.findAllInvoiceByTenantId(currentUser.getTenant().getId());

        BigDecimal totalInvoiced = invoices.stream()
                .filter(i -> issued(i.getStatus()))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(z(), BigDecimal::add);
        BigDecimal totalPaid = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.VOID)
                .map(i -> nz(i.getAmountPaid()))
                .reduce(z(), BigDecimal::add);
        BigDecimal totalOverdue = invoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .map(i -> nz(i.getAmountDue()))
                .reduce(z(), BigDecimal::add);
        BigDecimal outstanding = invoices.stream()
                .filter(i -> issued(i.getStatus()))
                .map(i -> nz(i.getAmountDue()))
                .reduce(z(), BigDecimal::add);

        long invoiceCount = invoices.stream().filter(i -> i.getStatus() != InvoiceStatus.VOID).count();
        long paidCount = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.PAID).count();
        long overdueCount = invoices.stream().filter(i -> i.getStatus() == InvoiceStatus.OVERDUE).count();

        // % change vs the previous calendar month.
        YearMonth thisMonth = YearMonth.from(LocalDate.now());
        YearMonth lastMonth = thisMonth.minusMonths(1);
        BigDecimal invoicedThis = sumInvoicedIn(invoices, thisMonth);
        BigDecimal invoicedLast = sumInvoicedIn(invoices, lastMonth);
        BigDecimal paidThis = sumPaidIn(currentUser, thisMonth);
        BigDecimal paidLast = sumPaidIn(currentUser, lastMonth);

        return new DashboardStats(
                scale(totalInvoiced), scale(totalPaid), scale(totalOverdue), scale(outstanding),
                currency, invoiceCount, paidCount, overdueCount,
                pctChange(invoicedLast, invoicedThis), pctChange(paidLast, paidThis));
    }

    @Cacheable(value = "revenue", key = "#currentUser.tenant.id",
            condition = "#currentUser.tenant != null")
    @Transactional(readOnly = true)
    public List<RevenuePoint> revenue(User currentUser) {
        List<RevenuePoint> points = new ArrayList<>();
        if (currentUser.getTenant() == null) {
            return points;
        }
        List<Invoice> invoices = invoiceRepo.findAllInvoiceByTenantId(currentUser.getTenant().getId());
        List<Payment> payments = paymentRepo.findByTenantIdOrderByPaidAtDesc(currentUser.getTenant().getId());

        YearMonth start = YearMonth.from(LocalDate.now()).minusMonths(REVENUE_MONTHS - 1);
        for (int i = 0; i < REVENUE_MONTHS; i++) {
            YearMonth ym = start.plusMonths(i);
            BigDecimal invoiced = sumInvoicedIn(invoices, ym);
            BigDecimal paid = payments.stream()
                    .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                    .filter(p -> p.getPaidAt() != null && YearMonth.from(p.getPaidAt().toLocalDate()).equals(ym))
                    .map(p -> nz(p.getAmount()))
                    .reduce(z(), BigDecimal::add);
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            points.add(new RevenuePoint(label, scale(invoiced), scale(paid)));
        }
        return points;
    }

    @Cacheable(value = "aging", key = "#currentUser.tenant.id",
            condition = "#currentUser.tenant != null")
    @Transactional(readOnly = true)
    public List<AgingBucket> aging(User currentUser) {
        // bucket label -> [amount, count]
        String[] labels = {"Current", "1-30 days", "31-60 days", "61-90 days", "90+ days"};
        BigDecimal[] amounts = {z(), z(), z(), z(), z()};
        long[] counts = {0, 0, 0, 0, 0};

        if (currentUser.getTenant() != null) {
            LocalDate today = LocalDate.now();
            List<Invoice> invoices = invoiceRepo.findAllInvoiceByTenantId(currentUser.getTenant().getId());
            for (Invoice inv : invoices) {
                if (!issued(inv.getStatus())) continue;
                BigDecimal due = nz(inv.getAmountDue());
                if (due.signum() <= 0) continue;

                long daysPast = java.time.temporal.ChronoUnit.DAYS.between(inv.getDueDate(), today);
                int idx = bucketIndex(daysPast);
                amounts[idx] = amounts[idx].add(due);
                counts[idx]++;
            }
        }

        List<AgingBucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new AgingBucket(labels[i], scale(amounts[i]), counts[i]));
        }
        return buckets;
    }

    // --- helpers -----------------------------------------------------------

    /** Bucket by days past due: <=0 Current, then 1-30 / 31-60 / 61-90 / 90+. */
    private static int bucketIndex(long daysPast) {
        if (daysPast <= 0) return 0;
        if (daysPast <= 30) return 1;
        if (daysPast <= 60) return 2;
        if (daysPast <= 90) return 3;
        return 4;
    }

    /** "Issued" = counts toward receivables (excludes DRAFT and VOID). */
    private static boolean issued(InvoiceStatus s) {
        return s != InvoiceStatus.DRAFT && s != InvoiceStatus.VOID;
    }

    private static BigDecimal sumInvoicedIn(List<Invoice> invoices, YearMonth ym) {
        return invoices.stream()
                .filter(i -> issued(i.getStatus()))
                .filter(i -> i.getIssueDate() != null && YearMonth.from(i.getIssueDate()).equals(ym))
                .map(i -> nz(i.getTotalAmount()))
                .reduce(z(), BigDecimal::add);
    }

    private BigDecimal sumPaidIn(User user, YearMonth ym) {
        if (user.getTenant() == null) return z();
        return paymentRepo.findByTenantIdOrderByPaidAtDesc(user.getTenant().getId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.COMPLETED)
                .filter(p -> p.getPaidAt() != null && YearMonth.from(p.getPaidAt().toLocalDate()).equals(ym))
                .map(p -> nz(p.getAmount()))
                .reduce(z(), BigDecimal::add);
    }

    /** Percentage change from prev to curr, rounded to 1 dp; 0 when prev is 0. */
    private static BigDecimal pctChange(BigDecimal prev, BigDecimal curr) {
        if (prev == null || prev.signum() == 0) {
            return z();
        }
        return curr.subtract(prev)
                .multiply(BigDecimal.valueOf(100))
                .divide(prev, 1, RoundingMode.HALF_UP);
    }

    private String currencyOf(User user) {
        if (user.getTenant() != null && user.getTenant().getDefaultCurrency() != null) {
            return user.getTenant().getDefaultCurrency();
        }
        return "MYR";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static BigDecimal z() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal scale(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }
}
