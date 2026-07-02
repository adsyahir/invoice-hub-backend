package com.adsyahir.invoice_hub_backend.dao;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepo extends JpaRepository<User, Long> {

    User findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findAllByTenantId(Long tenantId);

    // Paged search across name + email for the admin users table.
    Page<User> findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String fullName, String email, Pageable pageable);
}
