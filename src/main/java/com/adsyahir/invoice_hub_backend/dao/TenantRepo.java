package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantRepo extends JpaRepository<Tenant, Long> {

    Tenant findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Tenant> findByUuid(UUID uUid);
}
