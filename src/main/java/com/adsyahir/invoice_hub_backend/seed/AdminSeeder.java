package com.adsyahir.invoice_hub_backend.seed;

import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.enums.RoleName;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;

/**
 * Seeds the platform SUPER_ADMIN account. Runs after {@link RbacSeeder}
 * (@Order(2)) so the SUPER_ADMIN role already exists. Manual only: gated on the
 * --seed flag. Idempotent: skips if the account already exists.
 */
@Component
@Order(2)
public class AdminSeeder implements CommandLineRunner {

    private static final String ADMIN_FULL_NAME = "adam syahir";
    private static final String ADMIN_EMAIL = "adsyahir16@gmail.com";
    private static final String ADMIN_PASSWORD = "12345@In";

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final PasswordEncoder passwordEncoder;

    public AdminSeeder(UserRepo userRepo, RoleRepo roleRepo, PasswordEncoder passwordEncoder) {
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Manual only: skip unless started with the --seed flag.
        if (!Arrays.asList(args).contains("--seed")) {
            return;
        }

        // Idempotent: don't recreate (and don't reset the password) if it exists.
        if (userRepo.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        Role superAdmin = roleRepo.findByName(RoleName.SUPER_ADMIN.name());
        if (superAdmin == null) {
            throw new IllegalStateException(
                    "SUPER_ADMIN role not found — run the RBAC seeder first (it is @Order(1)).");
        }

        User admin = new User();
        admin.setFullName(ADMIN_FULL_NAME);
        admin.setEmail(ADMIN_EMAIL);
        admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setRole(superAdmin);
        // tenant left null on purpose: a platform SUPER_ADMIN is not scoped to a
        // single tenant (see V3 migration).
        userRepo.save(admin);
    }
}
