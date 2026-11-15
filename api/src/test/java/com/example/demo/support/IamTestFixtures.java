package com.example.demo.support;

import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit IAM grants for legacy test actors; no runtime role fallback is introduced. */
public final class IamTestFixtures {
  private IamTestFixtures() {}

  public static void seedLegacyRoles(JdbcTemplate jdbc) {
    jdbc.update("UPDATE admin_users SET global_admin=(role='ADMIN')");
    jdbc.update(
        "INSERT INTO iam_group_members SELECT id,1 FROM admin_users ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO iam_group_member_roles SELECT id,1,CASE WHEN role='VIEWER' THEN 2 ELSE 1 END FROM admin_users ON CONFLICT DO NOTHING");
  }
}
