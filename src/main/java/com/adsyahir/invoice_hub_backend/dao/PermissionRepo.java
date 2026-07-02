package com.adsyahir.invoice_hub_backend.dao;

import com.adsyahir.invoice_hub_backend.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepo extends JpaRepository<Permission, Long> {

    Permission findByName(String name);
}
