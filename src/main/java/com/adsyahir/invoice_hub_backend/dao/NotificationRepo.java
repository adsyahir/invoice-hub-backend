package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface NotificationRepo extends JpaRepository<Notification, Long> {

    // Newest-first feed for a tenant (Pageable caps the size).
    List<Notification> findByTenantIdOrderByCreatedAtDesc(Long tenantId, Pageable pageable);

    long countByTenantIdAndReadAtIsNull(Long tenantId);

    // Mark one notification read (tenant-scoped so it can't touch another org's row).
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now " +
            "WHERE n.id = :id AND n.tenant.id = :tenantId AND n.readAt IS NULL")
    int markRead(@Param("id") Long id, @Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);

    // Mark every unread notification for the tenant read.
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now " +
            "WHERE n.tenant.id = :tenantId AND n.readAt IS NULL")
    int markAllRead(@Param("tenantId") Long tenantId, @Param("now") LocalDateTime now);
}
