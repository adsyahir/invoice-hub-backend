package com.adsyahir.invoice_hub_backend.controller;

import com.adsyahir.invoice_hub_backend.model.UserPrincipal;
import com.adsyahir.invoice_hub_backend.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** The bell feed: newest notifications + unread count. Any authenticated user. */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> feed(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(notificationService.feed(principal.getUser()));
    }

    @PostMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markRead(@PathVariable Long id,
                                      @AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markRead(id, principal.getUser());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> markAllRead(@AuthenticationPrincipal UserPrincipal principal) {
        notificationService.markAllRead(principal.getUser());
        return ResponseEntity.noContent().build();
    }
}
