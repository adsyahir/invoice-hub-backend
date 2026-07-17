package com.adsyahir.invoice_hub_backend.search;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Invoice;
import com.adsyahir.invoice_hub_backend.model.InvoiceLineItem;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes the Elasticsearch documents. Called from SearchIndexConsumer (per-entity
 * refreshes off the Kafka topic) and SearchReindexRunner (full rebuild at startup).
 *
 * <p>Always loads CURRENT state from the database rather than trusting the event —
 * by the time a message is processed the invoice may already be voided, paid, or gone.
 * That also makes at-least-once redelivery harmless: same id in, same document out.
 *
 * <p>Transactional(readOnly) because mapping touches lazy associations (client, tenant,
 * lineItems) — they need a live Hibernate session, the same detached-proxy lesson as
 * InvoiceEmailService.
 */
@Service
@RequiredArgsConstructor
public class SearchIndexService {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexService.class);

    private final InvoiceRepo invoiceRepo;
    private final ClientRepo clientRepo;
    private final InvoiceSearchRepo invoiceSearchRepo;
    private final ClientSearchRepo clientSearchRepo;

    /**
     * Refresh one invoice document. If the row is gone (soft-deleted rows are invisible
     * to findById via @SQLRestriction), the document is dropped instead — so a delete
     * and an update are the same operation from the caller's point of view.
     */
    @Transactional(readOnly = true)
    public void indexInvoice(Long invoiceId) {
        invoiceRepo.findById(invoiceId).ifPresentOrElse(
                invoice -> invoiceSearchRepo.save(toDocument(invoice)),
                () -> invoiceSearchRepo.deleteById(invoiceId));
    }

    /**
     * Refresh one client document — and their invoice documents, because clientName is
     * denormalized into those (a rename must not leave stale names searchable).
     */
    @Transactional(readOnly = true)
    public void indexClient(Long clientId) {
        clientRepo.findById(clientId).ifPresentOrElse(
                client -> {
                    clientSearchRepo.save(toDocument(client));
                    List<InvoiceDocument> invoices = invoiceRepo.findAllByClientId(clientId)
                            .stream().map(this::toDocument).toList();
                    if (!invoices.isEmpty()) {
                        invoiceSearchRepo.saveAll(invoices);
                    }
                },
                () -> removeClient(clientId));
    }

    /** Client deleted: drop their document and every invoice document of theirs. */
    public void removeClient(Long clientId) {
        clientSearchRepo.deleteById(clientId);
        // The DB soft-deletes their invoices in the same transaction as the client, so
        // the rows are already invisible — delete the documents by the denormalized fk.
        invoiceSearchRepo.deleteByClientId(clientId);
    }

    /**
     * Rebuild both indices from the database. The index is a disposable projection;
     * this is what makes it safe to lose (wiped ES volume, mapping change, missed
     * events while ES was down).
     */
    @Transactional(readOnly = true)
    public void reindexAll() {
        List<ClientDocument> clients = clientRepo.findAll().stream().map(this::toDocument).toList();
        if (!clients.isEmpty()) {
            clientSearchRepo.saveAll(clients);
        }
        List<InvoiceDocument> invoices = invoiceRepo.findAll().stream().map(this::toDocument).toList();
        if (!invoices.isEmpty()) {
            invoiceSearchRepo.saveAll(invoices);
        }
        log.info("Search reindex complete: {} client(s), {} invoice(s)", clients.size(), invoices.size());
    }

    // --- entity -> document mapping ----------------------------------------

    private InvoiceDocument toDocument(Invoice inv) {
        return InvoiceDocument.builder()
                .id(inv.getId())
                .tenantId(inv.getTenant() != null ? inv.getTenant().getId() : null)
                .clientId(inv.getClient() != null ? inv.getClient().getId() : null)
                .uuid(inv.getUuid() != null ? inv.getUuid().toString() : null)
                .invoiceNumber(inv.getInvoiceNumber())
                .clientName(inv.getClient() != null ? inv.getClient().getName() : null)
                .status(inv.getStatus() != null ? inv.getStatus().name() : null)
                .currency(inv.getCurrency())
                .totalAmount(inv.getTotalAmount())
                .notes(inv.getNotes())   // customer-facing notes only; internalNotes stays out
                .lineItems(inv.getLineItems().stream()
                        .map(InvoiceLineItem::getDescription)
                        .toList())
                .issueDate(inv.getIssueDate())
                .dueDate(inv.getDueDate())
                .build();
    }

    private ClientDocument toDocument(Client c) {
        return ClientDocument.builder()
                .id(c.getId())
                .tenantId(c.getTenant() != null ? c.getTenant().getId() : null)
                .uuid(c.getUuid() != null ? c.getUuid().toString() : null)
                .name(c.getName())
                .email(c.getEmail())
                .phone(c.getPhone())
                .taxId(c.getTaxId())
                .build();
    }
}
