package com.example.demo.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.demo.common.authorization.AuthorizationService;
import com.example.demo.common.authorization.RequirePermission;
import com.example.demo.common.authorization.ResourceResolver;
import com.example.demo.iam.application.port.out.IamRepositoryPort;
import com.example.demo.support.HttpApiTestSupport;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Independent QA regressions for global-policy and generic annotation fail-open risks. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(IamSecurityRegressionTest.Probes.class)
class IamSecurityRegressionTest extends HttpApiTestSupport {
  @Autowired private AuthorizationService authorization;
  @MockitoSpyBean private IamRepositoryPort policyRepository;

  @MockitoSpyBean(name = "userResourceResolver")
  private ResourceResolver userResolver;

  private Session admin;
  private long target;

  @BeforeEach
  void fixture() throws Exception {
    ProbeController.invocations.set(0);
    jdbc.update("DELETE FROM iam_user_permission_overrides WHERE user_id=1");
    jdbc.update(
        "INSERT INTO iam_groups(id,name) VALUES(900,'QA isolated management group') ON CONFLICT(id) DO UPDATE SET enabled=TRUE");
    jdbc.update(
        "INSERT INTO admin_users(username,password_hash,display_name,role,email) SELECT 'qa_regression_target',password_hash,'QA regression target','VIEWER','qa-regression-target@example.test' FROM admin_users WHERE username='viewer' ON CONFLICT(username) DO NOTHING");
    target =
        jdbc.queryForObject(
            "SELECT id FROM admin_users WHERE username='qa_regression_target'", Long.class);
    jdbc.update(
        "INSERT INTO iam_group_members VALUES(1,900),(?,900) ON CONFLICT DO NOTHING", target);
    jdbc.update(
        "INSERT INTO iam_permissions(code,resource,action,name) VALUES('qa_probe:read','qa_probe','read','QA resolved probe'),('qa_unresolved:read','qa_unresolved','read','QA unresolved probe') ON CONFLICT(code) DO NOTHING");
    admin = login("admin");
  }

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM iam_user_permission_overrides WHERE user_id=1");
  }

  private void deny(long group, String code) {
    jdbc.update(
        "INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,reason) SELECT 1,?,id,'DENY','Independent QA DENY' FROM iam_permissions WHERE code=? ON CONFLICT(user_id,group_id,permission_id) DO UPDATE SET effect='DENY',expires_at=NULL",
        group,
        code);
  }

  @Test
  void globalManagementReadsAndWritesStillHonorGroupDeny() throws Exception {
    deny(900, "iam:user:manage");
    deny(900, "iam:group:manage");
    deny(900, "iam:audit:read");
    jdbc.update(
        "INSERT INTO iam_permission_audit_logs(actor_id,target_user_id,group_id,change_type,before_data,after_data,request_id) VALUES(1,?,900,'QA_FIXTURE','{}','{}',?)",
        target,
        UUID.randomUUID());
    mvc.perform(get("/api/v1/iam/users").cookie(admin.auth()).param("q", "qa_regression_target"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(get("/api/v1/iam/users/" + target).cookie(admin.auth()))
        .andExpect(status().isForbidden());
    var groups =
        mvc.perform(get("/api/v1/iam/groups").cookie(admin.auth()))
            .andExpect(status().isOk())
            .andReturn();
    for (var group : mapper.readTree(groups.getResponse().getContentAsString()).get("items"))
      assertThat(group.get("id").asLong()).isNotEqualTo(900);
    var audit =
        mvc.perform(
                get("/api/v1/iam/audit-logs")
                    .cookie(admin.auth())
                    .param("userId", Long.toString(target)))
            .andExpect(status().isOk())
            .andReturn();
    assertThat(
            mapper.readTree(audit.getResponse().getContentAsString()).get("totalElements").asLong())
        .isZero();
    mvc.perform(
            put("/api/v1/iam/groups/900")
                .cookie(admin.auth())
                .session(admin.csrfSession())
                .header("X-CSRF-TOKEN", admin.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"must not change\",\"enabled\":true}"))
        .andExpect(status().isForbidden());
    assertThat(jdbc.queryForObject("SELECT name FROM iam_groups WHERE id=900", String.class))
        .isEqualTo("QA isolated management group");
  }

  @Test
  void globalFlagCannotBypassPartialOrCompleteDenyForGlobalRoleMutation() throws Exception {
    deny(900, "iam:role:manage");
    mvc.perform(
            put("/api/v1/iam/roles/matrix")
                .cookie(admin.auth())
                .session(admin.csrfSession())
                .header("X-CSRF-TOKEN", admin.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[]}"))
        .andExpect(status().isForbidden());
    jdbc.update(
        "INSERT INTO iam_group_members(user_id,group_id) SELECT 1,id FROM iam_groups ON CONFLICT DO NOTHING");
    jdbc.update(
        "INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,reason) SELECT 1,g.id,p.id,'DENY','Independent all-group role DENY' FROM iam_groups g CROSS JOIN iam_permissions p WHERE p.code='iam:role:manage' ON CONFLICT(user_id,group_id,permission_id) DO UPDATE SET effect='DENY',expires_at=NULL");
    mvc.perform(
            put("/api/v1/iam/roles/matrix")
                .cookie(admin.auth())
                .session(admin.csrfSession())
                .header("X-CSRF-TOKEN", admin.csrfToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"roles\":[]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void allowedActionsUsesOneResolverBatchAndConstantPolicyQueriesAsResourceCountGrows() {
    clearInvocations(policyRepository, userResolver);
    authorization.getAllowedActions(1L, "user", List.of(1L), Set.of("read", "update"));
    long singlePolicyQueries =
        mockingDetails(policyRepository).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("query"))
            .count();
    verify(userResolver, times(1)).resolveGroupIds(List.of(1L));
    verify(userResolver, never()).resolveGroupId(any());
    clearInvocations(policyRepository, userResolver);
    var ids = LongStream.rangeClosed(1, 100).boxed().toList();
    var result = authorization.getAllowedActions(1L, "user", ids, Set.of("read", "update"));
    long batchPolicyQueries =
        mockingDetails(policyRepository).getInvocations().stream()
            .filter(invocation -> invocation.getMethod().getName().equals("query"))
            .count();
    assertThat(result).hasSize(100);
    assertThat(result.values())
        .allSatisfy(actions -> assertThat(actions).contains("read", "update"));
    assertThat(batchPolicyQueries).isEqualTo(singlePolicyQueries);
    verify(userResolver, times(1)).resolveGroupIds(ids);
    verify(userResolver, never()).resolveGroupId(any());
  }

  @Test
  void explicitGlobalLifecyclePolicyCanReactivateGroupsButNeverReadInactiveBusinessData()
      throws Exception {
    jdbc.update("UPDATE users SET group_id=900 WHERE id=123");
    try {
      mvc.perform(
              put("/api/v1/iam/groups/900")
                  .cookie(admin.auth())
                  .session(admin.csrfSession())
                  .header("X-CSRF-TOKEN", admin.csrfToken())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"QA isolated management group\",\"enabled\":false}"))
          .andExpect(status().isOk());
      var groups =
          mvc.perform(get("/api/v1/iam/groups").cookie(admin.auth()))
              .andExpect(status().isOk())
              .andReturn();
      boolean found = false;
      for (var group : mapper.readTree(groups.getResponse().getContentAsString()).get("items"))
        if (group.get("id").asLong() == 900) {
          found = true;
          assertThat(group.get("enabled").asBoolean()).isFalse();
        }
      assertThat(found).isTrue();
      mvc.perform(get("/api/v1/users/123").cookie(admin.auth())).andExpect(status().isForbidden());
      mvc.perform(
              put("/api/v1/iam/groups/900")
                  .cookie(admin.auth())
                  .session(admin.csrfSession())
                  .header("X-CSRF-TOKEN", admin.csrfToken())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"name\":\"QA isolated management group\",\"enabled\":true}"))
          .andExpect(status().isOk());
      mvc.perform(get("/api/v1/users/123").cookie(admin.auth())).andExpect(status().isOk());
    } finally {
      jdbc.update("UPDATE users SET group_id=1 WHERE id=123");
      jdbc.update("UPDATE iam_groups SET enabled=TRUE WHERE id=900");
    }
  }

  @Test
  void genericAnnotationAllowsValidResourceAndFailsClosedForMissingResolverResourceAndSpel()
      throws Exception {
    mvc.perform(get("/api/v1/iam-regression/probe/1").cookie(admin.auth()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reached").value(true));
    assertThat(ProbeController.invocations.get()).isEqualTo(1);
    mvc.perform(get("/api/v1/iam-regression/probe/999999").cookie(admin.auth()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/iam-regression/unresolved/1").cookie(admin.auth()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/iam-regression/spel/1").cookie(admin.auth()))
        .andExpect(status().isForbidden());
    assertThat(ProbeController.invocations.get()).isEqualTo(1);
  }

  @Test
  void genericAnnotationRejectsInvalidIdsAndRealGroupMismatchAndAnonymousCaller() throws Exception {
    mvc.perform(get("/api/v1/iam-regression/probe/0").cookie(admin.auth()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/iam-regression/probe/-1").cookie(admin.auth()))
        .andExpect(status().isBadRequest());
    mvc.perform(get("/api/v1/iam-regression/probe/1/group/900").cookie(admin.auth()))
        .andExpect(status().isForbidden());
    mvc.perform(get("/api/v1/iam-regression/probe/1/group/1").cookie(admin.auth()))
        .andExpect(status().isOk());
    mvc.perform(get("/api/v1/iam-regression/probe/1")).andExpect(status().isUnauthorized());
    assertThat(ProbeController.invocations.get()).isEqualTo(1);
  }

  @TestConfiguration
  static class Probes {
    @Bean
    ProbeController probeController() {
      return new ProbeController();
    }

    @Bean
    ResourceResolver probeResolver() {
      return new ResourceResolver() {
        public String supports() {
          return "qa_probe";
        }

        public Long resolveGroupId(Long id) {
          return id == 1L ? 1L : null;
        }

        public Map<Long, Long> resolveGroupIds(Collection<Long> ids) {
          return ids.contains(1L) ? Map.of(1L, 1L) : Map.of();
        }

        public void lock(Long id) {}
      };
    }
  }

  @TestComponent
  @RestController
  static class ProbeController {
    static final AtomicInteger invocations = new AtomicInteger();

    private Map<String, Boolean> reached() {
      invocations.incrementAndGet();
      return Map.of("reached", true);
    }

    @GetMapping("/api/v1/iam-regression/probe/{id}")
    @RequirePermission(resource = "qa_probe", action = "read", resourceId = "#id")
    public Map<String, Boolean> resolved(@PathVariable Long id) {
      return reached();
    }

    @GetMapping("/api/v1/iam-regression/probe/{id}/group/{groupId}")
    @RequirePermission(
        resource = "qa_probe",
        action = "read",
        resourceId = "#id",
        groupId = "#groupId")
    public Map<String, Boolean> grouped(@PathVariable Long id, @PathVariable Long groupId) {
      return reached();
    }

    @GetMapping("/api/v1/iam-regression/unresolved/{id}")
    @RequirePermission(resource = "qa_unresolved", action = "read", resourceId = "#id")
    public Map<String, Boolean> unresolved(@PathVariable Long id) {
      return reached();
    }

    @GetMapping("/api/v1/iam-regression/spel/{id}")
    @RequirePermission(resource = "qa_probe", action = "read", resourceId = "#missing.property")
    public Map<String, Boolean> invalidSpel(@PathVariable Long id) {
      return reached();
    }
  }
}
