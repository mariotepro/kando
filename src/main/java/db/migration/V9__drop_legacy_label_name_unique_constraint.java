package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Drops the single-column UNIQUE(name) constraint that V1 put on {@code label}, now replaced by
 * the composite UNIQUE(board_id, name) index added in V8.
 *
 * <p>A plain SQL {@code ALTER TABLE ... DROP CONSTRAINT} would need the constraint's name, and
 * Postgres and H2 (used in tests) auto-generate different names for an inline {@code UNIQUE}
 * column constraint. Looking it up via {@code information_schema}, which both databases expose
 * identically, avoids hardcoding either name.
 */
// Class name must match Flyway's V{version}__{description} convention, which it parses this
// name against to resolve the migration's version and description — a PascalCase name would
// break migration discovery, so the S101 naming-convention warning doesn't apply here.
@SuppressWarnings("java:S101")
public class V9__drop_legacy_label_name_unique_constraint extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String constraintName = null;
        try (PreparedStatement statement = context.getConnection().prepareStatement(
                "select constraint_name from information_schema.table_constraints "
                    + "where upper(table_name) = 'LABEL' and constraint_type = 'UNIQUE'")) {
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    constraintName = rs.getString(1);
                }
            }
        }

        // constraintName comes from information_schema (database metadata, not user input), but
        // identifiers can't be bound as PreparedStatement parameters, so it's interpolated below —
        // validate it looks like a plain SQL identifier first as defense in depth.
        if (constraintName == null || !constraintName.matches("[A-Za-z0-9_]+")) {
            return;
        }

        try (Statement statement = context.getConnection().createStatement()) {
            statement.execute("alter table label drop constraint \"" + constraintName + "\"");
        }
    }
}
