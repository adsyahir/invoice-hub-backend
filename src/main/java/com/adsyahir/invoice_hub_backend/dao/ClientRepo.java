package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepo extends JpaRepository<Client, Long> {

    List<Client> findAllByTenantId(Long tenantId);
    boolean existsByIdAndTenantId(Long Id, Long tenantId);
    boolean existsByUuidAndTenantId(UUID uuid, Long tenantId);
    // Tenant-scoped single lookup by the PUBLIC uuid: a client owned by another
    // tenant isn't found, so a user can't reach it by changing the URL.
    Optional<Client> findByUuidAndTenantId(UUID uuid, Long tenantId);
    Optional<Client> findByUuid(UUID uuid);

}
