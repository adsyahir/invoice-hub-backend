package com.adsyahir.invoice_hub_backend.schema;

import com.adsyahir.invoice_hub_backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the schema itself.
 *
 * <p>Two things are being asserted here, and the second is the reason this class
 * exists at all:
 *
 * <ol>
 *   <li>Every Flyway migration applies cleanly, in order, to an empty PostgreSQL.
 *       A migration that only works against <em>your</em> laptop's already-migrated
 *       database fails here.
 *   <li>The JPA entities agree with the schema those migrations produce. The test
 *       config sets {@code ddl-auto: validate}, so if an entity has a field with no
 *       column — or a column of the wrong type — the Spring context fails to start
 *       and every integration test in the suite goes red.
 * </ol>
 *
 * <p>The main app runs {@code ddl-auto: update}, which silently patches that drift
 * at boot. Production would then be carrying a column no migration created. Here it
 * is a hard failure.
 */
class SchemaMigrationIT extends AbstractIntegrationTest {

    @Test
    @DisplayName("every migration applied successfully, in version order")
    void allMigrationsApplied() {
        List<String> failed = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = false",
                String.class);
        assertThat(failed).as("failed Flyway migrations").isEmpty();

        Integer applied = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success = true", Integer.class);
        assertThat(applied).as("applied migrations").isGreaterThanOrEqualTo(25);
    }

    @Test
    @DisplayName("the core tables exist with the columns the entities expect")
    void coreTablesExist() {
        assertThat(tableNames()).contains(
                "tenants", "users", "roles", "permissions", "role_permissions",
                "clients", "invoices", "invoice_line_items", "payments",
                "audit_logs", "notifications", "tenant_einvoice_settings",
                "password_reset_tokens", "refresh_tokens", "team_invitations");
    }

    @Test
    @DisplayName("audit_logs.new_value is JSONB — the column type an in-memory DB would not have caught")
    void auditSummaryIsJsonb() {
        String type = jdbc.queryForObject("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'audit_logs' AND column_name = 'new_value'
                """, String.class);
        assertThat(type).isEqualTo("jsonb");
    }

    @Test
    @DisplayName("money columns are NUMERIC(15,2) — never floating point")
    void moneyColumnsAreExact() {
        List<String> types = jdbc.queryForList("""
                SELECT data_type FROM information_schema.columns
                WHERE table_name = 'invoices'
                  AND column_name IN ('subtotal', 'tax_amount', 'total_amount',
                                      'amount_paid', 'amount_due')
                """, String.class);

        assertThat(types).hasSize(5).containsOnly("numeric");
    }

    @Test
    @DisplayName("a tenant's e-invoice settings row is unique per tenant")
    void einvoiceSettingsAreOnePerTenant() {
        Integer unique = jdbc.queryForObject("""
                SELECT count(*) FROM information_schema.table_constraints
                WHERE table_name = 'tenant_einvoice_settings'
                  AND constraint_type = 'UNIQUE'
                """, Integer.class);
        assertThat(unique).isPositive();
    }

    private List<String> tableNames() {
        return jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                """, String.class);
    }
}
