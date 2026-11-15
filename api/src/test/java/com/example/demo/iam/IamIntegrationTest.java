package com.example.demo.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.authorization.AccessScope;
import com.example.demo.common.authorization.AuthorizationService;
import com.example.demo.support.HttpApiTestSupport;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class IamIntegrationTest extends HttpApiTestSupport {
  @Autowired AuthorizationService auth;

  @BeforeEach
  void fixture() {
    jdbc.update("UPDATE users SET group_id=1,status='ACTIVE',version=0");
    jdbc.update("DELETE FROM iam_permission_audit_logs");
    jdbc.update("DELETE FROM iam_group_members");
    jdbc.update("DELETE FROM iam_groups WHERE id<>1");
    jdbc.update("UPDATE iam_groups SET enabled=true");
    jdbc.update("UPDATE admin_users SET enabled=true,global_admin=(username='admin')");
    jdbc.update("DELETE FROM iam_role_permissions");
    jdbc.update("DELETE FROM iam_roles WHERE id>3");
    jdbc.update(
        "INSERT INTO iam_role_permissions SELECT 1,id FROM iam_permissions WHERE resource='user'");
    jdbc.update(
        "INSERT INTO iam_role_permissions SELECT 2,id FROM iam_permissions WHERE code='user:read'");
    jdbc.update(
        "INSERT INTO iam_role_permissions SELECT 3,id FROM iam_permissions WHERE resource IN('user','iam:user','iam:group','iam:permission','iam:audit')");
    jdbc.update("INSERT INTO iam_groups(id,name) VALUES(10,'Team A'),(20,'Team B')");
    jdbc.update("INSERT INTO iam_group_members SELECT id,1 FROM admin_users");
    jdbc.update(
        "INSERT INTO iam_group_member_roles SELECT id,1,CASE WHEN role='VIEWER' THEN 2 ELSE 1 END FROM admin_users");
    member(2, 10, 1);
    member(2, 20, 2);
    member(3, 10, 2);
    jdbc.update("UPDATE users SET group_id=10 WHERE id=101");
    jdbc.update("UPDATE users SET group_id=20 WHERE id=102");
  }

  private void member(long user, long group, long role) {
    jdbc.update("INSERT INTO iam_group_members VALUES(?,?) ON CONFLICT DO NOTHING", user, group);
    jdbc.update(
        "INSERT INTO iam_group_member_roles VALUES(?,?,?) ON CONFLICT DO NOTHING",
        user,
        group,
        role);
  }

  private long permission(String code) {
    return jdbc.queryForObject("SELECT id FROM iam_permissions WHERE code=?", Long.class, code);
  }

  private void override(long user, long group, String effect, String expiry) {
    jdbc.update(
        "INSERT INTO iam_user_permission_overrides VALUES(?,?,?, ?,CAST(? AS timestamptz),'test reason') ON CONFLICT(user_id,group_id,permission_id) DO UPDATE SET effect=EXCLUDED.effect,expires_at=EXCLUDED.expires_at",
        user,
        group,
        permission("user:update"),
        effect,
        expiry);
  }

  private MvcResult mutate(Session actor, String method, String path, Object request)
      throws Exception {
    return mvc.perform(
            request(org.springframework.http.HttpMethod.valueOf(method), path)
                .cookie(actor.auth())
                .session(actor.csrfSession())
                .header("X-CSRF-TOKEN", actor.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(request)))
        .andReturn();
  }

  @Test
  void rolesStayWithinEachMembershipAndUnknownDefaultsDeny() {
    assertThat(auth.can(2L, "user", "update", 101L)).isTrue();
    assertThat(auth.can(2L, "user", "update", 102L)).isFalse();
    assertThat(auth.can(2L, "user", "read", 102L)).isTrue();
    assertThat(auth.can(3L, "user", "update", 101L)).isFalse();
    assertThat(auth.can(2L, "unknown", "read", 101L)).isFalse();
  }

  @Test
  void directAllowAndDenyOverrideRolesAndExpiredOverridesAreIgnored() {
    override(3, 10, "ALLOW", null);
    assertThat(auth.can(3L, "user", "update", 101L)).isTrue();
    override(2, 10, "DENY", null);
    assertThat(auth.can(2L, "user", "update", 101L)).isFalse();
    override(2, 10, "DENY", "2000-01-01T00:00:00Z");
    assertThat(auth.can(2L, "user", "update", 101L)).isTrue();
  }

  @Test
  void globalDenyBeatsGlobalAllowAndScopesMatchBatch() {
    member(1, 10, 1);
    override(1, 10, "DENY", null);
    assertThat(auth.can(1L, "user", "update", 101L)).isFalse();
    assertThat(auth.getScope(1L, "user", "update").groupIds()).doesNotContain(10L).contains(20L);
    var actions = auth.getAllowedActions(1L, "user", List.of(101L, 102L), Set.of("read", "update"));
    assertThat(actions.get(101L)).containsExactly("read");
    assertThat(actions.get(102L)).contains("update");
  }

  @Test
  void immutableBatchInputsWorkAndMissingResourcesHaveNoActions() {
    var result =
        auth.getAllowedActions(2L, "user", List.of(101L, 102L, 999999L), Set.of("read", "update"));
    assertThat(result.get(101L)).contains("read", "update");
    assertThat(result.get(102L)).containsExactly("read");
    assertThat(result.get(999999L)).isEmpty();
  }

  @Test
  void absentMembershipInactiveGroupAndDisabledAccountDeny() {
    jdbc.update("DELETE FROM iam_group_members WHERE user_id=2 AND group_id=20");
    assertThat(auth.can(2L, "user", "read", 102L)).isFalse();
    jdbc.update("UPDATE iam_groups SET enabled=false WHERE id=10");
    assertThat(auth.can(1L, "user", "read", 101L)).isFalse();
    jdbc.update("UPDATE admin_users SET enabled=false WHERE id=2");
    assertThat(auth.getScope(2L, "user", "read").type()).isEqualTo(AccessScope.ScopeType.NONE);
  }

  @Test
  void sqlScopeFiltersItemsAndTotalBeforePagination() throws Exception {
    jdbc.update("DELETE FROM iam_group_members WHERE user_id=2 AND group_id=1");
    jdbc.update("DELETE FROM iam_group_members WHERE user_id=2 AND group_id=20");
    var actor = login("operator");
    mvc.perform(get("/api/v1/users").cookie(actor.auth()).param("size", "1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.items[0].id").value(101))
        .andExpect(jsonPath("$.items[0].groupId").value(10))
        .andExpect(jsonPath("$.items[0].allowedActions").isArray())
        .andExpect(jsonPath("$.hasNext").value(false));
  }

  @Test
  void noneScopeReturnsEmptyAndSingleInaccessibleOrMissingAlwaysDeny() throws Exception {
    jdbc.update("DELETE FROM iam_group_members WHERE user_id=3");
    var actor = login("viewer");
    mvc.perform(get("/api/v1/users").cookie(actor.auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.items").isEmpty());
    for (long id : List.of(101L, 999999L))
      mvc.perform(get("/api/v1/users/" + id).cookie(actor.auth()))
          .andExpect(status().isForbidden());
  }

  @Test
  void actualActionUsesDynamicAllowAndRecordsOnlyPermittedUpdate() throws Exception {
    var actor = login("viewer");
    assertThat(
            mutate(
                    actor,
                    "PATCH",
                    "/api/v1/users/101/status",
                    Map.of("status", "SUSPENDED", "version", 0))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    override(3, 10, "ALLOW", null);
    assertThat(
            mutate(
                    actor,
                    "PATCH",
                    "/api/v1/users/101/status",
                    Map.of("status", "SUSPENDED", "version", 0))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=101", String.class))
        .isEqualTo("SUSPENDED");
  }

  @Test
  void unauthorizedManagementIsBlockedAndGroupAdminsCannotElevateOrInspectOtherGroups()
      throws Exception {
    var viewer = login("viewer");
    mvc.perform(get("/api/v1/iam/roles").cookie(viewer.auth())).andExpect(status().isForbidden());
    member(2, 10, 3);
    var operator = login("operator");
    mvc.perform(
            get("/api/v1/iam/users/3/effective-permissions")
                .cookie(operator.auth())
                .param("groupId", "20"))
        .andExpect(status().isForbidden());
    assertThat(
            mutate(
                    operator,
                    "PUT",
                    "/api/v1/iam/groups/10/members/3",
                    Map.of("roleIds", List.of(3)))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            mutate(
                    operator,
                    "POST",
                    "/api/v1/iam/roles",
                    Map.of(
                        "code",
                        "BAD",
                        "name",
                        "Bad",
                        "permissionIds",
                        List.of(permission("user:read"))))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
  }

  @Test
  void atomicAccessChangesAreAuditedAndImmediatelyEffective() throws Exception {
    var admin = login("admin");
    var response =
        mutate(
            admin,
            "PUT",
            "/api/v1/iam/users/3/groups/10/access",
            Map.of(
                "roleIds",
                List.of(1),
                "overrides",
                List.of(
                    Map.of(
                        "permissionId",
                        permission("user:update"),
                        "effect",
                        "DENY",
                        "reason",
                        "separation of duties"))));
    assertThat(response.getResponse().getStatus()).isEqualTo(200);
    assertThat(auth.can(3L, "user", "update", 101L)).isFalse();
    var audit =
        jdbc.queryForMap(
            "SELECT actor_id,target_user_id,request_id,before_data::text b,after_data::text a FROM iam_permission_audit_logs");
    assertThat(audit.get("actor_id")).isEqualTo(1L);
    assertThat(audit.get("target_user_id")).isEqualTo(3L);
    assertThat(audit.get("request_id")).isEqualTo(requestId(response));
    assertThat(audit.get("a").toString()).contains("DENY", "separation of duties");
    mvc.perform(
            get("/api/v1/iam/users/3/effective-permissions")
                .cookie(admin.auth())
                .param("groupId", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[1].reason").value("EXPLICIT_DENY"));
  }

  @Test
  void removingOverrideRestoresInheritanceAndMembershipRemovalRevokesImmediately()
      throws Exception {
    var admin = login("admin");
    override(2, 10, "DENY", null);
    var request = new java.util.HashMap<String, Object>();
    request.put("groupId", 10);
    request.put("effect", null);
    assertThat(
            mutate(
                    admin,
                    "PUT",
                    "/api/v1/iam/users/2/overrides/" + permission("user:update"),
                    request)
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(auth.can(2L, "user", "update", 101L)).isTrue();
    assertThat(
            mutate(admin, "DELETE", "/api/v1/iam/groups/10/members/2", Map.of())
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(auth.can(2L, "user", "update", 101L)).isFalse();
  }

  @Test
  void matrixIsAtomicAndAssignedRoleDeletionConflicts() throws Exception {
    var admin = login("admin");
    long version = jdbc.queryForObject("SELECT version FROM iam_policy_version", Long.class);
    var response =
        mutate(
            admin,
            "PUT",
            "/api/v1/iam/roles/matrix",
            Map.of(
                "roles",
                List.of(
                    Map.of(
                        "id", 1, "code", "OPERATOR", "name", "Changed", "permissionIds", List.of()),
                    Map.of(
                        "id",
                        99999,
                        "code",
                        "MISSING",
                        "name",
                        "Missing",
                        "permissionIds",
                        List.of()))));
    assertThat(response.getResponse().getStatus()).isEqualTo(404);
    assertThat(auth.can(2L, "user", "update", 101L)).isTrue();
    assertThat(jdbc.queryForObject("SELECT version FROM iam_policy_version", Long.class))
        .isEqualTo(version);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM iam_permission_audit_logs", Long.class))
        .isZero();
    assertThat(mutate(admin, "DELETE", "/api/v1/iam/roles/1", Map.of()).getResponse().getStatus())
        .isEqualTo(409);
  }

  @Test
  void auditFailureRollsBackPolicyAndMembership() throws Exception {
    var admin = login("admin");
    jdbc.execute(
        "ALTER TABLE iam_permission_audit_logs ADD CONSTRAINT test_iam_audit_reject CHECK(false) NOT VALID");
    try {
      assertThat(
              mutate(admin, "PUT", "/api/v1/iam/groups/20/members/3", Map.of("roleIds", List.of(1)))
                  .getResponse()
                  .getStatus())
          .isEqualTo(500);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM iam_group_members WHERE user_id=3 AND group_id=20",
                  Long.class))
          .isZero();
    } finally {
      jdbc.execute("ALTER TABLE iam_permission_audit_logs DROP CONSTRAINT test_iam_audit_reject");
    }
  }

  @Test
  void globalLifecycleCanReactivateGroupButBusinessDataStaysDeniedWhileInactive() throws Exception {
    var admin = login("admin");
    jdbc.update("UPDATE iam_groups SET enabled=false WHERE id=10");
    assertThat(auth.can(1L, "user", "read", 101L)).isFalse();
    mvc.perform(get("/api/v1/iam/groups").cookie(admin.auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3));
    assertThat(
            mutate(
                    admin,
                    "PUT",
                    "/api/v1/iam/groups/10",
                    Map.of("name", "Team A restored", "enabled", true))
                .getResponse()
                .getStatus())
        .isEqualTo(200);
    assertThat(auth.can(1L, "user", "read", 101L)).isTrue();
  }

  @Test
  void inspectorRejectsClientGroupMismatchAndUnknownResolver() throws Exception {
    var admin = login("admin");
    for (String resource : List.of("user", "payment")) {
      var result =
          mutate(
              admin,
              "POST",
              "/api/v1/iam/check",
              Map.of(
                  "userId",
                  1,
                  "groupId",
                  20,
                  "resource",
                  resource,
                  "action",
                  "read",
                  "resourceId",
                  101));
      assertThat(result.getResponse().getStatus()).isEqualTo(200);
      assertThat(
              mapper.readTree(result.getResponse().getContentAsString()).get("allowed").asBoolean())
          .isFalse();
    }
  }

  @Test
  void concurrentCommittedRevocationIsRecheckedInsideActionTransaction() throws Exception {
    override(3, 10, "ALLOW", null);
    var actor = login("viewer");
    var executor = Executors.newSingleThreadExecutor();
    try (var connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement
            .executeQuery("SELECT version FROM iam_policy_version WHERE id=1 FOR UPDATE")
            .close();
        statement.executeUpdate(
            "UPDATE iam_user_permission_overrides SET effect='DENY' WHERE user_id=3 AND group_id=10");
        var result =
            executor.submit(
                () ->
                    mutate(
                        actor,
                        "PATCH",
                        "/api/v1/users/101/status",
                        Map.of("status", "SUSPENDED", "version", 0)));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        int waiting = 0;
        while (System.nanoTime() < deadline) {
          waiting =
              jdbc.queryForObject(
                  "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND query LIKE '%iam_policy_version%FOR UPDATE%'",
                  Integer.class);
          if (waiting > 0) break;
          Thread.sleep(20);
        }
        assertThat(waiting).isPositive();
        connection.commit();
        assertThat(result.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM users WHERE id=101", String.class))
            .isEqualTo("ACTIVE");
      } finally {
        connection.rollback();
      }
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void malformedExpiryAndDuplicateRoleAreClientErrors() throws Exception {
    var admin = login("admin");
    assertThat(
            mutate(
                    admin,
                    "PUT",
                    "/api/v1/iam/users/3/overrides/" + permission("user:update"),
                    Map.of(
                        "groupId",
                        10,
                        "effect",
                        "ALLOW",
                        "reason",
                        "why",
                        "expiresAt",
                        "yesterday"))
                .getResponse()
                .getStatus())
        .isEqualTo(400);
    assertThat(
            mutate(
                    admin,
                    "POST",
                    "/api/v1/iam/roles",
                    Map.of("code", "OPERATOR", "name", "Duplicate", "permissionIds", List.of()))
                .getResponse()
                .getStatus())
        .isEqualTo(409);
  }

  @Test
  void groupAdministratorCannotEraseSystemAccountDenyThroughMembershipCascade() throws Exception {
    member(2, 10, 3);
    member(1, 10, 1);
    jdbc.update(
        "INSERT INTO iam_user_permission_overrides VALUES(1,10,?,'DENY',NULL,'System restriction')",
        permission("iam:user:manage"));
    var operator = login("operator");
    assertThat(
            mutate(operator, "DELETE", "/api/v1/iam/groups/10/members/1", Map.of())
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(
            mutate(
                    operator,
                    "PUT",
                    "/api/v1/iam/groups/10/members/1",
                    Map.of("roleIds", List.of(1)))
                .getResponse()
                .getStatus())
        .isEqualTo(403);
    assertThat(auth.canInGroup(1L, "iam:user", "manage", 10L)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM iam_group_members WHERE user_id=1 AND group_id=10",
                Long.class))
        .isEqualTo(1);
  }
}
