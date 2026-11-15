package com.example.demo.common.request;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditPrivacyTest {
  @Test
  void onlyTypedAllowlistedMetadataCanBeStored() {
    var sanitized =
        AuditPrivacy.metadata(
            Map.of(
                "searched",
                true,
                "status",
                "ACTIVE",
                "size",
                20,
                "returnedCount",
                3,
                "password",
                "secret",
                "q",
                "person@example.test",
                "actorId",
                99,
                "payloadDigest",
                "not-a-hash"));
    assertThat(sanitized).containsOnlyKeys("searched", "status", "size", "returnedCount");
    assertThat(AuditPrivacy.metadata(Map.of("size", "secret", "status", "person@example.test")))
        .isEmpty();
  }

  @Test
  void pathsDropQueryAndUnknownPathsAreRedactedOrRejected() {
    assertThat(AuditPrivacy.requestPath("/api/v1/users?q=person@example.test"))
        .isEqualTo("/api/v1/users");
    assertThat(AuditPrivacy.pagePath("/users/123?token=secret#private")).isEqualTo("/users/123");
    assertThat(AuditPrivacy.requestPath("/api/v1/secret/person@example.test"))
        .isEqualTo("/api/v1/[unmatched]");
    assertThatThrownBy(() -> AuditPrivacy.pagePath("/unknown/person@example.test"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void clientPagePathIsPreservedOnlyAfterAllowlistAndQuerySanitization() {
    assertThat(AuditPrivacy.metadata(Map.of("pagePath", "/users/123?q=private#secret")))
        .containsEntry("pagePath", "/users/123");
    assertThat(AuditPrivacy.metadata(Map.of("pagePath", "/private/person@example.test")))
        .doesNotContainKey("pagePath");
  }

  @Test
  void iamPathsAreExplicitlyAllowlistedWithoutQueryOrUnknownSegments() {
    assertThat(AuditPrivacy.pagePath("/iam?user=private@example.test")).isEqualTo("/iam");
    for (String path :
        java.util.List.of(
            "/iam/me",
            "/iam/users",
            "/iam/users/2",
            "/iam/users/2/groups/10/access",
            "/iam/users/2/effective-permissions",
            "/iam/users/2/overrides/3",
            "/iam/groups/10/members/2",
            "/iam/roles/matrix",
            "/iam/permissions",
            "/iam/check",
            "/iam/audit-logs"))
      assertThat(AuditPrivacy.requestPath("/api/v1" + path + "?q=private"))
          .isEqualTo("/api/v1" + path);
    for (String path :
        java.util.List.of(
            "/iam/users/person@example.test",
            "/iam/users/2/private",
            "/iam/roles/matrix/extra",
            "/iam/groups/0/members/2"))
      assertThat(AuditPrivacy.requestPath("/api/v1" + path)).isEqualTo("/api/v1/[unmatched]");
    assertThatThrownBy(() -> AuditPrivacy.pagePath("/iam/private@example.test"))
        .isInstanceOf(RuntimeException.class);
  }

  @Test
  void userAgentIsBoundedAndControlCharactersRemoved() {
    assertThat(AuditPrivacy.userAgent("a\r\nb")).isEqualTo("ab");
    assertThat(AuditPrivacy.userAgent("a".repeat(600))).hasSize(512);
  }
}
