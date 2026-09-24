package com.nirvaankar.marketplace.identity;

import com.nirvaankar.marketplace.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the whole schema actually applies to a real MySQL 8, and that the
 * table names still match the architecture document. Renaming a table is a
 * decision, not an accident, so this test makes it visible.
 */
class SchemaMigrationTest extends AbstractIntegrationTest {

    private static final Set<String> EXPECTED_TABLES = Set.of(
            // identity
            "users", "user_identities", "user_profiles", "roles", "permissions",
            "role_permissions", "user_roles", "refresh_tokens", "otp_requests",
            "devices", "user_addresses",
            "geo_countries", "geo_states", "geo_districts", "geo_localities", "geo_pincodes",
            "payment_charge_configs",
            // seller
            "sellers", "seller_settings", "seller_bank_accounts", "seller_kyc_documents",
            // catalog
            "categories", "brands", "products", "product_variants", "attributes",
            "attribute_values", "variant_attribute_values", "product_images",
            "product_specifications", "catalog_import_jobs", "catalog_import_errors",
            "price_lists", "prices", "tax_categories", "product_rating_summary",
            // inventory
            "inventory_locations", "inventory_levels", "inventory_reservations",
            "inventory_transactions",
            // cart
            "carts", "cart_items", "buy_now_checkouts",
            // promotion
            "promotions", "promotion_rules", "promotion_redemptions", "order_discounts",
            // ordering
            "orders", "order_items", "order_item_taxes", "invoices",
            "invoice_sequences", "order_status_history",
            // payment
            "payments", "refunds", "idempotency_keys", "webhook_deliveries",
            // fulfilment
            "shipments", "shipment_items", "return_requests", "return_items",
            "shipping_providers", "seller_event_log",
            // ledger
            "ledger_accounts", "ledger_entries", "seller_payouts", "payout_items",
            // review
            "reviews", "review_replies", "wishlists", "wishlist_items",
            // sdui
            "themes", "theme_versions", "screens", "screen_versions", "sections",
            "media_assets", "cms_blocks", "translations", "banners", "config_publishes",
            // flags
            "feature_flags", "flag_rules", "experiments", "experiment_variants",
            "experiment_assignments",
            // platform
            "audit_logs", "outbox_events", "notifications", "notification_preferences",
            "app_releases", "search_queries", "audience_segments");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("every table from the architecture document exists after migration")
    void allDocumentedTablesArePresent() {
        List<String> actual = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()",
                String.class);

        assertThat(actual).containsAll(EXPECTED_TABLES);
    }

    @Test
    @DisplayName("inventory_levels.available is computed by the database, not by Java")
    void availableStockIsAGeneratedColumn() {
        String extra = jdbcTemplate.queryForObject("""
                SELECT extra FROM information_schema.columns
                 WHERE table_schema = DATABASE()
                   AND table_name = 'inventory_levels'
                   AND column_name = 'available'
                """, String.class);

        assertThat(extra).contains("STORED GENERATED");
    }

    @Test
    @DisplayName("a soft-deleted email is released for reuse")
    void softDeleteFreesTheUniqueEmail() {
        jdbcTemplate.update("""
                INSERT INTO users (public_id, email, password_hash, deleted_at)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'reuse@nirvaankar.test', 'x', NOW(6))
                """);
        // The same address must be insertable again, because the generated
        // uniqueness column goes NULL once the row is soft-deleted.
        jdbcTemplate.update("""
                INSERT INTO users (public_id, email, password_hash)
                VALUES (UNHEX(REPLACE(UUID(), '-', '')), 'reuse@nirvaankar.test', 'x')
                """);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE email = 'reuse@nirvaankar.test'", Integer.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("hot tables are range partitioned by month")
    void hotTablesArePartitioned() {
        List<String> partitioned = jdbcTemplate.queryForList("""
                SELECT DISTINCT table_name FROM information_schema.partitions
                 WHERE table_schema = DATABASE() AND partition_name IS NOT NULL
                """, String.class);

        assertThat(partitioned).contains(
                "orders", "audit_logs", "ledger_entries",
                "inventory_transactions", "search_queries");
    }
}
