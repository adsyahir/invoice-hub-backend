package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dto.response.InvoiceResponse;
import com.adsyahir.invoice_hub_backend.enums.EInvoiceStatus;
import com.adsyahir.invoice_hub_backend.enums.InvoiceStatus;
import com.adsyahir.invoice_hub_backend.model.User;
import jakarta.mail.internet.MimeMessage;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Spring AI tools that let the assistant answer from the workspace's REAL invoice
 * data. Instantiated PER REQUEST with the authenticated {@link User} bound, so
 * every query is tenant-scoped — the model can never reach another tenant's data.
 *
 * All reads go through {@link InvoiceService#list(User)}, which already enforces
 * the tenant boundary, so these tools add no new data-access paths.
 */
public class InvoiceAssistantTools {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");

    private final User user;
    private final InvoiceService invoiceService;
    private final JavaMailSender mailSender;

    public InvoiceAssistantTools(User user, InvoiceService invoiceService, JavaMailSender mailSender) {
        this.user = user;
        this.invoiceService = invoiceService;
        this.mailSender = mailSender;
    }

    @Tool(description = """
            Get a summary of this workspace's receivables: total invoiced, total
            collected, total outstanding, and the overdue count and amount. Use for
            questions about revenue, totals, or how the business is doing.""")
    public String getReceivablesSummary() {
        List<InvoiceResponse> invoices = invoiceService.list(user);
        if (invoices.isEmpty()) {
            return "This workspace has no invoices yet.";
        }
        String cur = currencyOf(invoices);

        BigDecimal invoiced = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.DRAFT && i.getStatus() != InvoiceStatus.VOID)
                .map(InvoiceResponse::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.VOID)
                .map(i -> nz(i.getAmountPaid()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outstanding = invoices.stream()
                .filter(i -> i.getStatus() != InvoiceStatus.VOID)
                .map(i -> nz(i.getAmountDue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InvoiceResponse> overdue = invoices.stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .toList();
        BigDecimal overdueAmount = overdue.stream()
                .map(i -> nz(i.getAmountDue()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return """
                Receivables summary:
                - Total invoiced: %s
                - Total collected: %s
                - Total outstanding: %s
                - Overdue: %s across %d invoice(s)"""
                .formatted(money(cur, invoiced), money(cur, collected),
                        money(cur, outstanding), money(cur, overdueAmount), overdue.size());
    }

    @Tool(description = """
            List this workspace's overdue invoices (invoice number, client, amount
            due, due date), most overdue first. Use when the user asks what is
            overdue, late, or past due.""")
    public String getOverdueInvoices() {
        List<InvoiceResponse> overdue = invoiceService.list(user).stream()
                .filter(i -> i.getStatus() == InvoiceStatus.OVERDUE)
                .sorted((a, b) -> a.getDueDate().compareTo(b.getDueDate()))
                .toList();
        if (overdue.isEmpty()) {
            return "There are no overdue invoices — everything sent is either paid or still within its payment terms.";
        }
        String cur = currencyOf(overdue);
        return overdue.stream()
                .map(i -> "- %s | %s | %s | due %s".formatted(
                        i.getInvoiceNumber(),
                        i.getClient() != null ? i.getClient().getName() : "Unknown client",
                        money(cur, nz(i.getAmountDue())),
                        i.getDueDate()))
                .collect(Collectors.joining("\n", "Overdue invoices (most overdue first):\n", ""));
    }

    @Tool(description = """
            List the clients with the largest outstanding (unpaid) balances, highest
            first. Use when the user asks who owes the most, about debtors, or top
            receivables by client.""")
    public String getTopOutstandingClients() {
        Map<String, BigDecimal> byClient = new LinkedHashMap<>();
        List<InvoiceResponse> invoices = invoiceService.list(user);
        for (InvoiceResponse i : invoices) {
            if (i.getStatus() == InvoiceStatus.VOID || i.getStatus() == InvoiceStatus.PAID) {
                continue;
            }
            BigDecimal due = nz(i.getAmountDue());
            if (due.signum() <= 0) {
                continue;
            }
            String name = i.getClient() != null ? i.getClient().getName() : "Unknown client";
            byClient.merge(name, due, BigDecimal::add);
        }
        if (byClient.isEmpty()) {
            return "Nothing outstanding — every issued invoice has been settled.";
        }
        String cur = currencyOf(invoices);
        return byClient.entrySet().stream()
                .sorted((a, b) -> b.getValue().compareTo(a.getValue()))
                .limit(5)
                .map(e -> "- %s | %s".formatted(e.getKey(), money(cur, e.getValue())))
                .collect(Collectors.joining("\n", "Clients with the largest outstanding balances:\n", ""));
    }

    @Tool(description = """
            Break down invoices by their LHDN MyInvois e-invoice status (Validated,
            Pending LHDN, Rejected, Not submitted, Cancelled). Use for MyInvois or
            e-invoice compliance questions.""")
    public String getEInvoiceStatusBreakdown() {
        List<InvoiceResponse> invoices = invoiceService.list(user);
        if (invoices.isEmpty()) {
            return "This workspace has no invoices yet.";
        }
        Map<EInvoiceStatus, Long> counts = new LinkedHashMap<>();
        for (InvoiceResponse i : invoices) {
            EInvoiceStatus st = i.getEinvoiceStatus() != null ? i.getEinvoiceStatus() : EInvoiceStatus.NOT_SUBMITTED;
            counts.merge(st, 1L, Long::sum);
        }
        StringBuilder sb = new StringBuilder("MyInvois e-invoice status breakdown:\n");
        for (EInvoiceStatus st : EInvoiceStatus.values()) {
            Long c = counts.get(st);
            if (c != null) {
                sb.append("- ").append(st.getLabel()).append(": ").append(c).append('\n');
            }
        }
        return sb.toString().stripTrailing();
    }

    // --- action tools (agentic: these DO things) ---------------------------

    @Tool(description = """
            Draft (but DO NOT send) a payment-reminder email for one invoice, by its
            invoice number. Returns the recipient, subject and body so the user can
            review it. ALWAYS use this to prepare a reminder before sending one.""")
    public String draftPaymentReminder(String invoiceNumber) {
        InvoiceResponse inv = findByNumber(invoiceNumber);
        if (inv == null) {
            return "No invoice found with number '" + invoiceNumber + "' in this workspace.";
        }
        String email = inv.getClient() != null ? inv.getClient().getEmail() : null;
        String to = (email != null && !email.isBlank()) ? email : "(no email on file for this client)";
        return "DRAFT reminder (NOT sent yet):\nTo: %s\nSubject: %s\n\n%s"
                .formatted(to, reminderSubject(inv), reminderBody(inv));
    }

    @Tool(description = """
            SEND a payment-reminder email for one invoice, by its invoice number, to
            the client's email address. Only call this AFTER the user has reviewed the
            draft and explicitly confirmed they want it sent. Never send without an
            explicit confirmation.""")
    public String sendPaymentReminder(String invoiceNumber) {
        InvoiceResponse inv = findByNumber(invoiceNumber);
        if (inv == null) {
            return "No invoice found with number '" + invoiceNumber + "' in this workspace.";
        }
        // Safety: never chase an invoice that's already settled or voided.
        if (inv.getStatus() == InvoiceStatus.PAID) {
            return "Invoice " + invoiceNumber + " is already paid — no reminder sent.";
        }
        if (inv.getStatus() == InvoiceStatus.VOID) {
            return "Invoice " + invoiceNumber + " is void — no reminder sent.";
        }
        String email = inv.getClient() != null ? inv.getClient().getEmail() : null;
        if (email == null || email.isBlank()) {
            return "No email address on file for the client on invoice " + invoiceNumber + " — cannot send.";
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom("no-reply@invoicehub.local");
            helper.setTo(email);
            helper.setSubject(reminderSubject(inv));
            helper.setText(reminderBody(inv), false);
            mailSender.send(message);
            return "Reminder sent to " + email + " for invoice " + invoiceNumber + ".";
        } catch (Exception e) {
            return "Failed to send reminder for invoice " + invoiceNumber + ": " + e.getMessage();
        }
    }

    private InvoiceResponse findByNumber(String invoiceNumber) {
        if (invoiceNumber == null) return null;
        String target = invoiceNumber.trim();
        return invoiceService.list(user).stream()
                .filter(i -> i.getInvoiceNumber() != null && i.getInvoiceNumber().equalsIgnoreCase(target))
                .findFirst()
                .orElse(null);
    }

    private String reminderSubject(InvoiceResponse inv) {
        return "Payment reminder: invoice " + inv.getInvoiceNumber();
    }

    private String reminderBody(InvoiceResponse inv) {
        String cur = inv.getCurrency() != null ? inv.getCurrency() : "MYR";
        String client = inv.getClient() != null ? inv.getClient().getName() : "there";
        return """
                Dear %s,

                This is a friendly reminder that invoice %s for %s is currently
                outstanding (due %s). We'd appreciate settlement at your earliest
                convenience.

                If you've already made payment, please disregard this message.

                Thank you,
                %s"""
                .formatted(client, inv.getInvoiceNumber(), money(cur, nz(inv.getAmountDue())),
                        inv.getDueDate(), user.getFullName());
    }

    // --- helpers -----------------------------------------------------------

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    /** The dominant currency in the set (first non-null), defaulting to MYR. */
    private static String currencyOf(List<InvoiceResponse> invoices) {
        return invoices.stream()
                .map(InvoiceResponse::getCurrency)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse("MYR");
    }

    private static String money(String currency, BigDecimal amount) {
        String prefix = "MYR".equalsIgnoreCase(currency) ? "RM" : currency + " ";
        return prefix + MONEY.format(nz(amount));
    }
}
