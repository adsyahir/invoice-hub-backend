package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentRepo extends JpaRepository<Payment, Long> {

    // All live payments for a tenant, newest first (@SQLRestriction filters soft-deleted rows).
    List<Payment> findByTenantIdOrderByPaidAtDesc(Long tenantId);

    // Every payment recorded against one invoice — used to recompute the invoice's
    // paid/due totals from scratch (source of truth is the payment rows).
    List<Payment> findByInvoiceId(Long invoiceId);

    // Tenant-scoped single lookup by the PUBLIC uuid (IDOR guard): a payment owned
    // by another tenant simply isn't found.
    Optional<Payment> findByUuidAndTenantId(UUID uuid, Long tenantId);
}
