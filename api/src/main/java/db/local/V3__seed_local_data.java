package db.local;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.sql.PreparedStatement;

/** Loaded only by the explicit local Flyway location; never part of the common schema. */
public class V3__seed_local_data extends BaseJavaMigration {
    @Override
    public void migrate(Context context) throws Exception {
        var connection = context.getConnection();
        var passwordEncoder = new BCryptPasswordEncoder();
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO admin_users(username,password_hash,display_name,role) VALUES (?,?,?,?)")) {
            for (String role : new String[]{"ADMIN", "OPERATOR", "VIEWER"}) {
                statement.setString(1, role.toLowerCase(java.util.Locale.ROOT));
                statement.setString(2, passwordEncoder.encode("local-test-password"));
                statement.setString(3, "Local " + role);
                statement.setString(4, role);
                statement.addBatch();
            }
            statement.executeBatch();
        }
        try (var statement = connection.createStatement()) {
            // Let the database identity generate IDs, including 123; the sequence remains valid.
            statement.executeUpdate("INSERT INTO users(email,name) SELECT 'member' || n || '@example.test', 'Member ' || n FROM generate_series(1,200) AS n");
        }
    }
}
