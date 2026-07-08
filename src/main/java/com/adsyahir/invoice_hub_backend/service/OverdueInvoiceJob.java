package com.adsyahir.invoice_hub_backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled sweep that marks past-due unpaid invoices as OVERDUE (US-013).
 * Runs once a day at 01:00 server time. The actual state transition lives in
 * {@link InvoiceService#markOverdueInvoices()} so it can also be triggered
 * manually or from a test.
 */
@Component
public class OverdueInvoiceJob {

    private final InvoiceService invoiceService;

    public OverdueInvoiceJob(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // Daily at 01:00. cron = second minute hour day-of-month month day-of-week.
    @Scheduled(cron = "0 0 1 * * *")
    public void sweep() {
        invoiceService.markOverdueInvoices();
    }
}
