package com.civictrack.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;

/**
 * Safe, non-destructive upgrade of an EXISTING civictrack database (runs on every start, idempotent):
 *  1. Hibernate 6 created MySQL ENUM columns for status/category. The new statuses/categories would be rejected,
 *     so those columns are converted to VARCHAR (all existing values are kept).
 *  2. Back-fills SLA deadlines and the photo table for complaints created before the upgrade.
 * No rows are deleted and no table is dropped.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class SchemaMigrator implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        try {
            String product = jdbc.execute((java.sql.Connection con) -> con.getMetaData().getDatabaseProductName());
            if (product == null || !product.toLowerCase().contains("mysql")) return;   // embedded H2 starts fresh with VARCHAR columns
            toVarchar("complaints", "status", 30);
            toVarchar("complaints", "category", 30);
            toVarchar("status_history", "status", 30);
            toVarchar("complaints", "priority", 20);

            int sla = jdbc.update("UPDATE complaints SET sla_due_at = DATE_ADD(created_at, INTERVAL CASE priority "
                    + "WHEN 'CRITICAL' THEN 24 WHEN 'HIGH' THEN 72 WHEN 'LOW' THEN 336 ELSE 168 END HOUR) "
                    + "WHERE sla_due_at IS NULL AND created_at IS NOT NULL");
            int ph = jdbc.update("INSERT INTO complaint_photos (complaint_id, url, uploaded_at) "
                    + "SELECT c.id, c.image_path, COALESCE(c.created_at, NOW()) FROM complaints c "
                    + "WHERE c.image_path IS NOT NULL AND NOT EXISTS "
                    + "(SELECT 1 FROM complaint_photos p WHERE p.complaint_id = c.id AND p.resolution_id IS NULL)");
            if (sla > 0 || ph > 0) log.info("Schema upgrade: back-filled {} SLA deadline(s) and {} photo row(s)", sla, ph);
        } catch (Exception e) {
            log.error("Automatic schema upgrade failed - run database/migration_v2.sql manually. Cause: {}", e.toString());
        }
    }

    private void toVarchar(String table, String column, int length) {
        String type = jdbc.query("SELECT DATA_TYPE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                + "AND TABLE_NAME = ? AND COLUMN_NAME = ?",
                (ResultSetExtractor<String>) rs -> rs.next() ? rs.getString(1) : null, table, column);
        if ("enum".equalsIgnoreCase(type)) {
            jdbc.execute("ALTER TABLE `" + table + "` MODIFY COLUMN `" + column + "` VARCHAR(" + length + ") NOT NULL");
            log.info("Converted {}.{} from ENUM to VARCHAR({})", table, column, length);
        }
    }
}
