package com.adsyahir.invoice_hub_backend.seed;

import com.adsyahir.invoice_hub_backend.dao.PermissionRepo;
import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.model.Permission;
import com.adsyahir.invoice_hub_backend.model.Role;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Seeds the RBAC reference data (permissions, roles, role→permission mappings)
 * on startup. Idempotent: it upserts by name, so it is safe to run on every
 * boot and acts as the single source of truth for what each role can do — edit
 * the maps below and restart to re-sync.
 */
@Component
@Order(1)
public class RbacSeeder implements CommandLineRunner {

    private final PermissionRepo permissionRepo;
    private final RoleRepo roleRepo;

    public RbacSeeder(PermissionRepo permissionRepo, RoleRepo roleRepo) {
        this.permissionRepo = permissionRepo;
        this.roleRepo = roleRepo;
    }

    /** Permission name -> human description. Insertion order preserved. */
    private static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();

    /** Role name -> human description. */
    private static final Map<String, String> ROLE_DESCRIPTIONS = new LinkedHashMap<>();

    /** Role name -> the permission names it grants. */
    private static final Map<String, Set<String>> ROLE_PERMISSIONS = new LinkedHashMap<>();

    static {
        PERMISSIONS.put("invoice:read", "View invoices");
        PERMISSIONS.put("invoice:write", "Create and edit invoices");
        PERMISSIONS.put("invoice:void", "Void / cancel invoices");
        PERMISSIONS.put("client:read", "View clients");
        PERMISSIONS.put("client:write", "Create and edit clients");
        PERMISSIONS.put("client:delete", "Delete clients");
        PERMISSIONS.put("payment:read", "View payments");
        PERMISSIONS.put("payment:record", "Record offline payments");
        PERMISSIONS.put("payment:refund", "Issue refunds");
        PERMISSIONS.put("report:read", "View reports");
        PERMISSIONS.put("team:read", "View team members");
        PERMISSIONS.put("team:manage", "Invite and manage members");
        PERMISSIONS.put("settings:manage", "Manage organization settings");
        PERMISSIONS.put("tenant:manage", "Administer all tenants (platform)");

        ROLE_DESCRIPTIONS.put("SUPER_ADMIN", "Platform administrator");
        ROLE_DESCRIPTIONS.put("TENANT_ADMIN", "Organization administrator");
        ROLE_DESCRIPTIONS.put("ACCOUNTANT", "Manages invoices, clients and payments");
        ROLE_DESCRIPTIONS.put("VIEWER", "Read-only access");

        // SUPER_ADMIN: every permission.
        ROLE_PERMISSIONS.put("SUPER_ADMIN", new LinkedHashSet<>(PERMISSIONS.keySet()));

        // TENANT_ADMIN: everything tenant-scoped (all except platform admin).
        Set<String> tenantAdmin = new LinkedHashSet<>(PERMISSIONS.keySet());
        tenantAdmin.remove("tenant:manage");
        ROLE_PERMISSIONS.put("TENANT_ADMIN", tenantAdmin);

        // ACCOUNTANT: manage invoices/clients/payments + reports, view team.
        ROLE_PERMISSIONS.put("ACCOUNTANT", new LinkedHashSet<>(Set.of(
                "invoice:read", "invoice:write", "invoice:void",
                "client:read", "client:write",
                "payment:read", "payment:record", "payment:refund",
                "report:read", "team:read")));

        // VIEWER: read-only.
        ROLE_PERMISSIONS.put("VIEWER", new LinkedHashSet<>(Set.of(
                "invoice:read", "client:read", "payment:read", "report:read", "team:read")));
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Manual only: skip unless started with the --seed flag.
        if (!Arrays.asList(args).contains("--seed")) {
            return;
        }

        // Upsert by name: existing rows are looked up and reused, so re-running
        // never creates duplicates — it just keeps the data in sync.
        Map<String, Permission> byName = new HashMap<>();
        PERMISSIONS.forEach((name, description) -> {
            Permission permission = permissionRepo.findByName(name);
            if (permission == null) {
                permission = new Permission();
                permission.setName(name);
            }
            permission.setDescription(description);
            byName.put(name, permissionRepo.save(permission));
        });

        // Upsert roles and (re)sync their permission sets.
        ROLE_PERMISSIONS.forEach((roleName, permissionNames) -> {
            Role role = roleRepo.findByName(roleName);
            if (role == null) {
                role = new Role();
                role.setName(roleName);
            }
            role.setDescription(ROLE_DESCRIPTIONS.get(roleName));

            Set<Permission> permissions = new HashSet<>();
            for (String permissionName : permissionNames) {
                permissions.add(byName.get(permissionName));
            }
            role.setPermissions(permissions);
            roleRepo.save(role);
        });
    }
}
