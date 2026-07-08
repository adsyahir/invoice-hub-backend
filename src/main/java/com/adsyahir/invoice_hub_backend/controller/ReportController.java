package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAuthority('report:read')")
    public ResponseEntity<?> dashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reportService.dashboard(principal.getUser()));
    }

    @GetMapping("/revenue")
    @PreAuthorize("hasAuthority('report:read')")
    public ResponseEntity<?> revenue(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reportService.revenue(principal.getUser()));
    }

    @GetMapping("/aging")
    @PreAuthorize("hasAuthority('report:read')")
    public ResponseEntity<?> aging(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(reportService.aging(principal.getUser()));
    }
}
