package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.dao.PaymentRepo;
import com.adsyahir.invoice_hub_backend.dto.request.CreatePaymentRequest;
import com.adsyahir.invoice_hub_backend.model.Payment;
import com.adsyahir.invoice_hub_backend.model.User;
import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/payments")

public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody CreatePaymentRequest request, @AuthenticationPrincipal UserPrincipal principal){

        Payment payment = paymentService.create(request,principal.getUser());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", payment.getUuid()
        ));
    }
}
