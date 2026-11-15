package com.example.demo.iam.adapter.out.persistence;

import com.example.demo.iam.application.port.out.IamRepositoryPort;
import com.example.demo.iam.application.query.IamReadOperation;
import com.example.demo.iam.application.query.IamWriteOperation;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class IamPersistenceAdapter implements IamRepositoryPort {
  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectMapper mapper;

  public IamPersistenceAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  public List<Map<String, Object>> query(IamReadOperation operation, Map<String, ?> parameters) {
    try {
      return jdbc.queryForList(readSql(operation), parameters);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new com.example.demo.common.exception.ApiException(
          com.example.demo.common.exception.ErrorCode.VERSION_CONFLICT);
    }
  }

  public int update(IamWriteOperation operation, Map<String, ?> parameters) {
    try {
      return jdbc.update(writeSql(operation), parameters);
    } catch (org.springframework.dao.DuplicateKeyException e) {
      throw new com.example.demo.common.exception.ApiException(
          com.example.demo.common.exception.ErrorCode.VERSION_CONFLICT);
    }
  }

  public void lockPolicy() {
    jdbc.queryForList("SELECT version FROM iam_policy_version WHERE id=1 FOR UPDATE", Map.of());
  }

  public void advancePolicy() {
    jdbc.update("UPDATE iam_policy_version SET version=version+1 WHERE id=1", Map.of());
  }

  public void audit(
      long actorId,
      Long targetUserId,
      Long groupId,
      String type,
      Object before,
      Object after,
      UUID requestId) {
    try {
      var p = new HashMap<String, Object>();
      p.put("actor", actorId);
      p.put("target", targetUserId);
      p.put("group", groupId);
      p.put("type", type);
      p.put("before", mapper.writeValueAsString(before));
      p.put("after", mapper.writeValueAsString(after));
      p.put("request", requestId);
      jdbc.update(
          "INSERT INTO iam_permission_audit_logs(actor_id,target_user_id,group_id,change_type,before_data,after_data,request_id) VALUES(:actor,:target,:group,:type,CAST(:before AS jsonb),CAST(:after AS jsonb),:request)",
          p);
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize permission audit", e);
    }
  }

  private String readSql(IamReadOperation operation) {
    return switch (operation) {
      case FIND_GLOBAL_ACCOUNT ->
"""
SELECT id FROM admin_users WHERE id=:id AND enabled AND global_admin
""";
      case ACTIVE_GROUP_IDS ->
"""
SELECT id FROM iam_groups WHERE(enabled OR :includeInactive)
""";
      case FIND_ENABLED_ACCOUNT ->
"""
SELECT id,global_admin FROM admin_users WHERE id=:id AND enabled
""";
      case SEARCH_ACCOUNTS ->
"""
SELECT a.id,a.username,a.email,a.display_name AS "displayName",a.enabled,a.global_admin AS "globalAdmin" FROM admin_users a WHERE(a.username ILIKE :query ESCAPE '!' OR a.email ILIKE :query ESCAPE '!' OR a.display_name ILIKE :query ESCAPE '!') AND(CAST(:enabled AS boolean) IS NULL OR a.enabled=CAST(:enabled AS boolean)) AND(EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id AND m.group_id IN(:groups)) OR(:all AND NOT EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id))) ORDER BY a.id LIMIT :limit OFFSET :offset
""";
      case COUNT_ACCOUNTS ->
"""
SELECT count(*) n FROM admin_users a WHERE(a.username ILIKE :query ESCAPE '!' OR a.email ILIKE :query ESCAPE '!' OR a.display_name ILIKE :query ESCAPE '!') AND(CAST(:enabled AS boolean) IS NULL OR a.enabled=CAST(:enabled AS boolean)) AND(EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id AND m.group_id IN(:groups)) OR(:all AND NOT EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id)))
""";
      case BATCH_MEMBERSHIPS ->
"""
SELECT m.user_id,g.id AS "groupId",g.name AS "groupName" FROM iam_group_members m JOIN iam_groups g ON g.id=m.group_id WHERE m.user_id IN(:ids) AND m.group_id IN(:groups) ORDER BY m.user_id,g.id
""";
      case BATCH_MEMBER_ROLES ->
"""
SELECT mr.user_id,mr.group_id,r.id,r.code,r.name FROM iam_group_member_roles mr JOIN iam_roles r ON r.id=mr.role_id WHERE mr.user_id IN(:ids) AND mr.group_id IN(:groups) ORDER BY mr.user_id,mr.group_id,r.id
""";
      case FIND_SCOPED_ACCOUNT ->
"""
SELECT a.id,a.username,a.email,a.display_name AS "displayName",a.enabled,a.global_admin AS "globalAdmin" FROM admin_users a WHERE a.id=:id AND(EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id AND m.group_id IN(:groups)) OR(:all AND NOT EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=a.id)))
""";
      case MANAGED_GROUPS ->
"""
SELECT g.id,g.name,g.enabled,(SELECT count(*) FROM iam_group_members m WHERE m.group_id=g.id) AS "memberCount" FROM iam_groups g WHERE g.id IN(:groups) ORDER BY g.id
""";
      case FIND_GROUP ->
"""
SELECT id,name,enabled FROM iam_groups WHERE id=:id
""";
      case CREATE_GROUP ->
"""
INSERT INTO iam_groups(name,enabled) VALUES(:name,:enabled) RETURNING id,name,enabled
""";
      case UPDATE_GROUP ->
"""
UPDATE iam_groups SET name=:name,enabled=:enabled WHERE id=:id RETURNING id,name,enabled
""";
      case ROLE_SUMMARIES ->
"""
SELECT r.id,r.code,r.name,(SELECT count(DISTINCT mr.user_id) FROM iam_group_member_roles mr WHERE mr.role_id=r.id) AS "affectedUserCount" FROM iam_roles r ORDER BY r.id
""";
      case ALL_ROLE_PERMISSIONS ->
"""
SELECT role_id,permission_id FROM iam_role_permissions
""";
      case FIND_PERMISSION ->
"""
SELECT id FROM iam_permissions WHERE id=:id
""";
      case CREATE_ROLE ->
"""
INSERT INTO iam_roles(code,name) VALUES(:code,:name) RETURNING id,code,name
""";
      case UPDATE_ROLE ->
"""
UPDATE iam_roles SET code=:code,name=:name WHERE id=:id RETURNING id,code,name
""";
      case FIND_ROLE ->
"""
SELECT id,code,name FROM iam_roles WHERE id=:id
""";
      case ROLE_PERMISSION_IDS ->
"""
SELECT permission_id FROM iam_role_permissions WHERE role_id=:id ORDER BY permission_id
""";
      case ROLE_ASSIGNMENT_EXISTS ->
"""
SELECT 1 FROM iam_group_member_roles WHERE role_id=:id LIMIT 1
""";
      case SEARCH_PERMISSIONS ->
"""
SELECT id,code,resource,action,name FROM iam_permissions WHERE(CAST(:resource AS varchar) IS NULL OR resource=:resource) AND(CAST(:action AS varchar) IS NULL OR action=:action) ORDER BY id
""";
      case ACCOUNT_EXISTS ->
"""
SELECT id,global_admin,enabled FROM admin_users WHERE id=:id
""";
      case EFFECTIVE_PERMISSIONS ->
"""
SELECT p.id AS "permissionId",p.code,p.resource,p.action,p.name,a.enabled AS user_enabled,a.global_admin,g.enabled AS group_enabled,
 EXISTS(SELECT 1 FROM iam_group_members m WHERE m.user_id=:user AND m.group_id=:group) member,
 EXISTS(SELECT 1 FROM iam_group_member_roles mr JOIN iam_role_permissions rp ON rp.role_id=mr.role_id WHERE mr.user_id=:user AND mr.group_id=:group AND rp.permission_id=p.id) inherited,
 o.effect AS "override",o.expires_at AS "expiresAt",o.reason AS "overrideReason",(o.expires_at IS NULL OR o.expires_at>statement_timestamp()) override_active
 FROM iam_permissions p CROSS JOIN admin_users a CROSS JOIN iam_groups g
 LEFT JOIN iam_user_permission_overrides o ON o.permission_id=p.id AND o.user_id=:user AND o.group_id=:group
 WHERE a.id=:user AND g.id=:group ORDER BY p.id
""";
      case ROLE_EXISTS ->
"""
SELECT id FROM iam_roles WHERE id=:id
""";
      case ROLE_CAPABILITIES ->
"""
SELECT p.resource,p.action FROM iam_role_permissions rp JOIN iam_permissions p ON p.id=rp.permission_id WHERE rp.role_id=:id
""";
      case MEMBERSHIP_SNAPSHOT ->
"""
SELECT user_id,group_id FROM iam_group_members WHERE user_id=:user AND group_id=:group
""";
      case MEMBER_ROLE_SNAPSHOT ->
"""
SELECT role_id FROM iam_group_member_roles WHERE user_id=:user AND group_id=:group ORDER BY role_id
""";
      case OVERRIDE_SNAPSHOT ->
"""
SELECT permission_id,effect,expires_at,reason FROM iam_user_permission_overrides WHERE user_id=:user AND group_id=:group ORDER BY permission_id
""";
      case PERMISSION_CAPABILITY ->
"""
SELECT resource,action FROM iam_permissions WHERE id=:id
""";
      case SEARCH_AUDIT ->
"""
SELECT id,actor_id AS "actorId",target_user_id AS "targetUserId",group_id AS "groupId",change_type AS "changeType",before_data::text AS "before",after_data::text AS "after",occurred_at AS "occurredAt",request_id AS "requestId" FROM iam_permission_audit_logs WHERE(group_id IN(:groups) OR(group_id IS NULL AND :all)) AND(CAST(:actor AS bigint) IS NULL OR actor_id=:actor) AND(CAST(:user AS bigint) IS NULL OR target_user_id=:user) AND(CAST(:from AS timestamptz) IS NULL OR occurred_at>=:from) AND(CAST(:to AS timestamptz) IS NULL OR occurred_at<=:to) ORDER BY occurred_at DESC,id DESC LIMIT :limit OFFSET :offset
""";
      case COUNT_AUDIT ->
"""
SELECT count(*) n FROM iam_permission_audit_logs WHERE(group_id IN(:groups) OR(group_id IS NULL AND :all)) AND(CAST(:actor AS bigint) IS NULL OR actor_id=:actor) AND(CAST(:user AS bigint) IS NULL OR target_user_id=:user) AND(CAST(:from AS timestamptz) IS NULL OR occurred_at>=:from) AND(CAST(:to AS timestamptz) IS NULL OR occurred_at<=:to)
""";
      case ENABLED_POLICY_ACCOUNT ->
"""
SELECT global_admin FROM admin_users WHERE id=:id AND enabled=TRUE
""";
      case POLICY_PERMISSION ->
"""
SELECT id FROM iam_permissions WHERE resource=:r AND action=:a
""";
      case GROUP_POLICY ->
"""
SELECT g.id,g.enabled,(m.user_id IS NOT NULL) member,o.effect,
    EXISTS(SELECT 1 FROM iam_group_member_roles mr JOIN iam_role_permissions rp ON rp.role_id=mr.role_id WHERE mr.user_id=:user AND mr.group_id=g.id AND rp.permission_id=:permission) inherited
   FROM iam_groups g LEFT JOIN iam_group_members m ON m.group_id=g.id AND m.user_id=:user
   LEFT JOIN iam_user_permission_overrides o ON o.group_id=g.id AND o.user_id=:user AND o.permission_id=:permission AND(o.expires_at IS NULL OR o.expires_at>statement_timestamp())
   WHERE(g.enabled=TRUE OR :includeInactive)
""";
      case ACTIVE_POLICY_GROUP_IDS ->
"""
SELECT id FROM iam_groups WHERE(enabled OR :includeInactive)
""";
    };
  }

  private String writeSql(IamWriteOperation operation) {
    return switch (operation) {
      case DELETE_ROLE_PERMISSIONS ->
"""
DELETE FROM iam_role_permissions WHERE role_id=:id
""";
      case ADD_ROLE_PERMISSION ->
"""
INSERT INTO iam_role_permissions VALUES(:role,:permission)
""";
      case DELETE_ROLE ->
"""
DELETE FROM iam_roles WHERE id=:id
""";
      case ADD_MEMBERSHIP ->
"""
INSERT INTO iam_group_members(user_id,group_id) VALUES(:user,:group) ON CONFLICT DO NOTHING
""";
      case DELETE_MEMBER_ROLES ->
"""
DELETE FROM iam_group_member_roles WHERE user_id=:user AND group_id=:group
""";
      case ADD_MEMBER_ROLE ->
"""
INSERT INTO iam_group_member_roles VALUES(:user,:group,:role)
""";
      case DELETE_OVERRIDE ->
"""
DELETE FROM iam_user_permission_overrides WHERE user_id=:user AND group_id=:group AND permission_id=:permission
""";
      case UPSERT_OVERRIDE ->
"""
INSERT INTO iam_user_permission_overrides(user_id,group_id,permission_id,effect,expires_at,reason) VALUES(:user,:group,:permission,:effect,:expiry,:reason) ON CONFLICT(user_id,group_id,permission_id) DO UPDATE SET effect=EXCLUDED.effect,expires_at=EXCLUDED.expires_at,reason=EXCLUDED.reason
""";
      case REMOVE_MEMBERSHIP ->
"""
DELETE FROM iam_group_members WHERE user_id=:user AND group_id=:group
""";
    };
  }
}
