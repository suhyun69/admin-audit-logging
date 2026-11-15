package com.example.demo.iam;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.demo.support.PostgresTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class IamMigrationTest extends PostgresTestSupport {
  @Test
  void existingV3AccountsAndBusinessDataUpgradeWithoutReplacingAuthentication() {
    var ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    var original =
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration", "classpath:db/local")
            .target("3")
            .load();
    original.migrate();
    var jdbc = new JdbcTemplate(ds);
    String hash =
        jdbc.queryForObject(
            "SELECT password_hash FROM admin_users WHERE username='admin'", String.class);
    var upgraded =
        Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration", "classpath:db/local")
            .load();
    assertThat(upgraded.migrate().migrationsExecuted).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT password_hash FROM admin_users WHERE username='admin'", String.class))
        .isEqualTo(hash);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM users WHERE group_id=1", Long.class))
        .isEqualTo(200);
    assertThat(
            jdbc.queryForObject(
                "SELECT email FROM admin_users WHERE username='admin'", String.class))
        .isEqualTo("admin@local.invalid");
    assertThat(
            jdbc.queryForObject(
                "SELECT global_admin FROM admin_users WHERE username='admin'", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT count(*) FROM iam_group_member_roles", Long.class))
        .isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM iam_permissions", Long.class))
        .isEqualTo(7);
    assertThat(upgraded.migrate().migrationsExecuted).isZero();
    jdbc.update(
        "INSERT INTO admin_users(username,password_hash,display_name,role,email) VALUES('new-admin','hash','New','ADMIN','new@example.test')");
    assertThat(
            jdbc.queryForObject(
                "SELECT global_admin FROM admin_users WHERE username='new-admin'", Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM iam_group_members m JOIN admin_users a ON a.id=m.user_id WHERE a.username='new-admin'",
                Long.class))
        .isZero();
  }
}
