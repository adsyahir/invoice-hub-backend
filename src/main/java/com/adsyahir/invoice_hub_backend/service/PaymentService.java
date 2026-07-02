package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.InvoiceRepo;
import com.adsyahir.invoice_hub_backend.dao.PaymentRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreatePaymentRequest;
import com.adsyahir.invoice_hub_backend.enums.PaymentStatus;
import com.adsyahir.invoice_hub_backend.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
@Service
public class PaymentService {

    @Autowired
    private PaymentRepo paymentRepo;

    @Autowired
    private InvoiceRepo invoiceRepo;

    @Transactional
    public Payment create(CreatePaymentRequest request, User currentUser) {

        System.out.println(currentUser.getTenant().getId());
        if (currentUser.getTenant().getId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found");
        }

        Invoice invoice = invoiceRepo
                .findByUuidAndTenantId(request.getInvoiceId(), currentUser.getTenant().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        Payment payment = Payment.builder()
                .tenant(currentUser.getTenant())
                .invoice(invoice)
                .amount(request.getAmount())
                .currency(invoice.getCurrency())
                .method(request.getMethod())
                .status(PaymentStatus.COMPLETED)
                .gateway("MANUAL")
                .reference(request.getReference())
                .recordedBy(currentUser)
                .paidAt(LocalDateTime.now())
                .build();

        return paymentRepo.save(payment);
    }
}
