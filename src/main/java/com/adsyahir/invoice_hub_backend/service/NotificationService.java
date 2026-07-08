package com.adsyahir.invoice_hub_backend.service;

import com.adsyahir.invoice_hub_backend.dao.NotificationRepo;
import com.adsyahir.invoice_hub_backend.dto.response.NotificationResponses.NotificationFeed;
import com.adsyahir.invoice_hub_backend.dto.response.NotificationResponses.NotificationItem;
import com.adsyahir.invoice_hub_backend.model.Notification;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Creates and reads in-app notifications (topbar bell). Notifications are
 * tenant-scoped — every member of the org sees the same feed. Writing a
 * notification must never break the business action that triggered it, so
 * {@link #notify} runs in its own transaction and swallows failures (same
 * pattern as {@link AuditService}).
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int DEFAULT_LIMIT = 20;

    private final NotificationRepo notificationRepo;

    public NotificationService(NotificationRepo notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    /** Raise a notification for a tenant. Best-effort — never throws. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Tenant tenant, String type, String title, String message, String link) {
        if (tenant == null) {
            return;
        }
        try {
            notificationRepo.save(Notification.builder()
                    .tenant(tenant)
                    .type(type)
                    .title(title)
                    .message(message)
                    .link(link)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to write notification ({}) for tenant {}: {}",
                    type, tenant.getId(), e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public NotificationFeed feed(User currentUser) {
        if (currentUser.getTenant() == null) {
            return new NotificationFeed(List.of(), 0);
        }
        Long tenantId = currentUser.getTenant().getId();
        List<NotificationItem> items = notificationRepo
                .findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, DEFAULT_LIMIT))
                .stream()
                .map(this::toItem)
                .toList();
        long unread = notificationRepo.countByTenantIdAndReadAtIsNull(tenantId);
        return new NotificationFeed(items, unread);
    }

    @Transactional
    public void markRead(Long id, User currentUser) {
        if (currentUser.getTenant() == null) return;
        notificationRepo.markRead(id, currentUser.getTenant().getId(), LocalDateTime.now());
    }

    @Transactional
    public void markAllRead(User currentUser) {
        if (currentUser.getTenant() == null) return;
        notificationRepo.markAllRead(currentUser.getTenant().getId(), LocalDateTime.now());
    }

    private NotificationItem toItem(Notification n) {
        return new NotificationItem(
                n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                n.getReadAt() != null, n.getCreatedAt());
    }
}
