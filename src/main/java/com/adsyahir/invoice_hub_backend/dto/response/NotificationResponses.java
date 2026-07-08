package com.adsyahir.invoice_hub_backend.dto.response;

import java.time.LocalDateTime;
import java.util.List;

/** Notification DTOs for the topbar bell. */
public final class NotificationResponses {

    private NotificationResponses() {}

    /** One notification as shown in the feed. */
    public record NotificationItem(
            Long id,
            String type,
            String title,
            String message,
            String link,
            boolean read,
            LocalDateTime createdAt
    ) {}

    /** The bell payload: the newest notifications plus the unread badge count. */
    public record NotificationFeed(List<NotificationItem> items, long unreadCount) {}
}
