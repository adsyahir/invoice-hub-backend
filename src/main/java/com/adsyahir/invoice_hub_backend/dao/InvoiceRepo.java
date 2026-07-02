package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepo extends JpaRepository<Invoice, Long> {

    boolean existsByInvoiceNumber(String invoiceNumber);

    // All live invoices for a tenant. @SQLRestriction already filters out
    // soft-deleted rows, so this returns only deleted_at IS NULL invoices.
    List<Invoice> findAllInvoiceByTenantId(Long tenantId);

    // Cascade soft delete: flip every live invoice of a client in one statement.
    // Bulk JPQL bypasses @SQLDelete, so we set deleted_at directly and guard on
    // deletedAt IS NULL ourselves (bulk updates aren't filtered by @SQLRestriction).
    @Modifying
    @Query("UPDATE Invoice i SET i.deletedAt = CURRENT_TIMESTAMP " +
            "WHERE i.client.id = :clientId AND i.deletedAt IS NULL")
    int softDeleteByClientId(@Param("clientId") Long clientId);

    // Tenant-scoped single lookup by the PUBLIC uuid: the tenant id is part of
    // the WHERE clause, so an invoice owned by another tenant simply isn't found.
    Optional<Invoice> findByUuidAndTenantId(UUID uuid, Long tenantId);

}
