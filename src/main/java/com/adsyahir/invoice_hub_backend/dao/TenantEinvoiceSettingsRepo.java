package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.TenantEinvoiceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TenantEinvoiceSettingsRepo extends JpaRepository<TenantEinvoiceSettings, Long> {

    Optional<TenantEinvoiceSettings> findByTenantId(Long tenantId);
}
