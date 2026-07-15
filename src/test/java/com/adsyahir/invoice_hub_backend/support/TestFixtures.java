package com.adsyahir.invoice_hub_backend.support;

import com.adsyahir.invoice_hub_backend.dao.ClientRepo;
import com.adsyahir.invoice_hub_backend.dao.PermissionRepo;
import com.adsyahir.invoice_hub_backend.dao.RoleRepo;
import com.adsyahir.invoice_hub_backend.dao.TenantRepo;
import com.adsyahir.invoice_hub_backend.dao.UserRepo;
import com.adsyahir.invoice_hub_backend.dto.LineItemRequest;
import com.adsyahir.invoice_hub_backend.dto.request.CreateInvoiceRequest;
import com.adsyahir.invoice_hub_backend.dto.request.CreatePaymentRequest;
import com.adsyahir.invoice_hub_backend.enums.PaymentMethod;
import com.adsyahir.invoice_hub_backend.model.Client;
import com.adsyahir.invoice_hub_backend.model.Permission;
import com.adsyahir.invoice_hub_backend.model.Role;
import com.adsyahir.invoice_hub_backend.model.Tenant;
import com.adsyahir.invoice_hub_backend.model.User;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Builders for the domain objects the integration tests need. Lives in src/test,
 * so it is only component-scanned by the test context.
 *
 * <p>The production seeders (RbacSeeder, AdminSeeder, GeoSeeder) are all gated on
 * a {@code --seed} command-line flag and therefore do not run under
 * {@code @SpringBootTest} — every test builds exactly the rows it needs.
 */
@Component
public class TestFixtures {

    private final TenantRepo tenantRepo;
    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final ClientRepo clientRepo;
    private final PermissionRepo permissionRepo;

    public TestFixtures(TenantRepo tenantRepo, UserRepo userRepo, RoleRepo roleRepo,
                        ClientRepo clientRepo, PermissionRepo permissionRepo) {
        this.tenantRepo = tenantRepo;
        this.userRepo = userRepo;
        this.roleRepo = roleRepo;
        this.clientRepo = clientRepo;
        this.permissionRepo = permissionRepo;
    }

    /** An organization. */
    public Tenant tenant(String name, String slug) {
        Tenant tenant = new Tenant();
        tenant.setName(name);
        tenant.setSlug(slug);
        tenant.setBillingEmail("billing@" + slug + ".test");
        tenant.setTaxId("C" + slug.hashCode());
        return tenantRepo.save(tenant);
    }

    /** A role granting the given permission names (created on demand). */
    public Role role(String name, String... permissionNames) {
        Role role = new Role();
        role.setName(name);
        role.setDescription(name);
        for (String permissionName : permissionNames) {
            Permission permission = permissionRepo.findByName(permissionName);
            if (permission == null) {
                permission = new Permission();
                permission.setName(permissionName);
                permission.setDescription(permissionName);
                permission = permissionRepo.save(permission);
            }
            role.getPermissions().add(permission);
        }
        return roleRepo.save(role);
    }

    /** A tenant-scoped user. Pass a null tenant for a platform SUPER_ADMIN. */
    public User user(String email, Tenant tenant, Role role) {
        User user = new User();
        user.setEmail(email);
        user.setPassword("{noop}irrelevant-for-service-tests");
        user.setFullName(email.substring(0, email.indexOf('@')));
        user.setRole(role);
        user.setTenant(tenant);
        return userRepo.save(user);
    }

    /** A billable customer of the tenant. */
    public Client client(Tenant tenant, String name, String email) {
        Client client = Client.builder()
                .tenant(tenant)
                .name(name)
                .email(email)
                .phone("+60123456789")
                .currency("MYR")
                .paymentTermsDays(30)
                .country("MY")
                .build();
        return clientRepo.save(client);
    }

    /**
     * A create-invoice payload for one line item. The service recomputes every
     * amount from these inputs — the caller never sends totals.
     */
    public CreateInvoiceRequest invoiceRequest(Client client, String invoiceNumber,
                                               long quantity, String unitPrice, String taxRate,
                                               LocalDate issueDate, LocalDate dueDate) {
        LineItemRequest line = new LineItemRequest();
        line.setDescription("Consulting services");
        line.setQuantity(quantity);
        line.setUnitPrice(new BigDecimal(unitPrice));
        line.setTaxRate(new BigDecimal(taxRate));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setInvoiceNumber(invoiceNumber);
        request.setClientId(String.valueOf(client.getId()));
        request.setCurrency("MYR");
        request.setIssueDate(issueDate);
        request.setDueDate(dueDate);
        request.setLineItems(List.of(line));
        return request;
    }

    /** RM 1,000.00 @ 8% SST = RM 1,080.00 total, issued today, due in 30 days. */
    public CreateInvoiceRequest invoiceRequest(Client client, String invoiceNumber) {
        return invoiceRequest(client, invoiceNumber, 1L, "1000.00", "8.00",
                LocalDate.now(), LocalDate.now().plusDays(30));
    }

    /** A manual payment payload against an invoice's public uuid. */
    public CreatePaymentRequest paymentRequest(UUID invoiceUuid, String amount) {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setInvoiceId(invoiceUuid);
        request.setAmount(new BigDecimal(amount));
        request.setMethod(PaymentMethod.BANK_TRANSFER);
        request.setReference("TXN-" + amount);
        return request;
    }
}
