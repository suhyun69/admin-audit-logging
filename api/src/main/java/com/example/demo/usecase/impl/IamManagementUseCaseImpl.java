package com.example.demo.usecase.impl;

import com.example.demo.common.authorization.AccessScope;
import com.example.demo.common.authorization.AuthorizationService;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import com.example.demo.iam.application.port.out.IamRepositoryPort;
import com.example.demo.iam.application.query.IamReadOperation;
import com.example.demo.iam.application.query.IamWriteOperation;
import com.example.demo.usecase.IamManagementUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class IamManagementUseCaseImpl implements IamManagementUseCase {
  private final IamRepositoryPort db;
  private final AuthorizationService auth;
  private final ObjectMapper mapper;

  public IamManagementUseCaseImpl(
      IamRepositoryPort db, AuthorizationService auth, ObjectMapper mapper) {
    this.db = db;
    this.auth = auth;
    this.mapper = mapper;
  }

  private long actor() {
    return RequestContext.current()
        .map(RequestContext::actor)
        .map(a -> a.id())
        .orElseThrow(
            () -> new AuthenticationCredentialsNotFoundException("Authentication required"));
  }

  private boolean global() {
    return !db.query(IamReadOperation.FIND_GLOBAL_ACCOUNT, Map.of("id", actor())).isEmpty();
  }

  private void requireGlobalRolePolicy() {
    if (auth.getScope(actor(), "iam:role", "manage").type() != AccessScope.ScopeType.ALL)
      throw new AccessDeniedException("Global role policy denied");
  }

  private void requireGlobal() {
    if (!global()) throw new AccessDeniedException("Explicit global administration required");
  }

  private void groupAccess(long group, String resource, String action) {
    if (!auth.canInGroup(actor(), resource, action, group))
      throw new AccessDeniedException("Group permission required");
  }

  private Set<Long> scope(String resource, String action) {
    var s = auth.getScope(actor(), resource, action);
    if (s.type() == AccessScope.ScopeType.ALL) {
      var ids = new HashSet<Long>();
      for (var r :
          db.query(
              IamReadOperation.ACTIVE_GROUP_IDS,
              Map.of("includeInactive", resource.equals("iam:group") && action.equals("manage"))))
        ids.add(number(r.get("id")));
      return ids;
    }
    return s.groupIds();
  }

  private void requireSome(String resource, String action) {
    if (scope(resource, action).isEmpty()) throw new AccessDeniedException("Permission required");
  }

  private long number(Object value) {
    if (!(value instanceof Number n) || n.longValue() <= 0 || n.doubleValue() != n.longValue())
      throw new IllegalArgumentException("Positive identifier required");
    return n.longValue();
  }

  private String text(Map<String, Object> request, String key, int max) {
    var value = request.get(key);
    if (!(value instanceof String s) || s.isBlank() || s.length() > max)
      throw new IllegalArgumentException("Invalid " + key);
    return s.trim();
  }

  private Instant parseInstant(String value) {
    try {
      return Instant.parse(value);
    } catch (java.time.DateTimeException e) {
      throw new IllegalArgumentException("Invalid timestamp");
    }
  }

  private void page(int page, int size) {
    if (page < 0 || size < 1 || size > 100 || page > 1000000)
      throw new IllegalArgumentException("Invalid page");
  }

  private List<Long> ids(Object value) {
    if (!(value instanceof Collection<?> values) || values.size() > 1000)
      throw new IllegalArgumentException("Expected identifiers");
    var ids = new LinkedHashSet<Long>();
    for (var v : values) ids.add(number(v));
    return List.copyOf(ids);
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> objects(Object value) {
    if (!(value instanceof Collection<?> values) || values.size() > 1000)
      throw new IllegalArgumentException("Expected entries");
    var result = new ArrayList<Map<String, Object>>();
    for (var v : values) {
      if (!(v instanceof Map<?, ?>)) throw new IllegalArgumentException("Expected entry");
      result.add((Map<String, Object>) v);
    }
    return result;
  }

  private Map<String, Object> one(IamReadOperation operation, Map<String, ?> p) {
    var rows = db.query(operation, p);
    if (rows.isEmpty()) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND);
    return new LinkedHashMap<>(rows.getFirst());
  }

  private void audit(Long target, Long group, String type, Object before, Object after) {
    db.advancePolicy();
    db.audit(
        actor(),
        target,
        group,
        type,
        before,
        after,
        RequestContext.current().orElseThrow().requestId());
  }

  public Map<String, Object> me() {
    long actor = actor();
    var account = one(IamReadOperation.FIND_ENABLED_ACCOUNT, Map.of("id", actor));
    return Map.of(
        "userId",
        actor,
        "globalAdmin",
        account.get("global_admin"),
        "manageableGroupIds",
        scope("iam:user", "manage"));
  }

  public Map<String, Object> users(String q, Boolean enabled, int page, int size) {
    page(page, size);
    requireSome("iam:user", "manage");
    if (q != null && q.length() > 100) throw new IllegalArgumentException("Search too long");
    var scope = auth.getScope(actor(), "iam:user", "manage");
    var groups = scope("iam:user", "manage");
    var p = new HashMap<String, Object>();
    p.put(
        "query",
        "%" + (q == null ? "" : q.replace("!", "!!").replace("%", "!%").replace("_", "!_")) + "%");
    p.put("limit", size);
    p.put("offset", (long) page * size);
    p.put("groups", groups.isEmpty() ? Set.of(-1L) : groups);
    p.put("enabled", enabled);
    p.put("all", scope.type() == AccessScope.ScopeType.ALL);

    var rows = db.query(IamReadOperation.SEARCH_ACCOUNTS, p);
    return Map.of(
        "items",
        enrichUsers(rows, groups),
        "totalElements",
        numberOrZero(one(IamReadOperation.COUNT_ACCOUNTS, p).get("n")),
        "page",
        page,
        "size",
        size);
  }

  private List<Map<String, Object>> enrichUsers(
      List<Map<String, Object>> accounts, Set<Long> groups) {
    if (accounts.isEmpty()) return List.of();
    var userIds = accounts.stream().map(a -> a.get("id")).toList();
    var p =
        Map.<String, Object>of("ids", userIds, "groups", groups.isEmpty() ? Set.of(-1L) : groups);
    var memberships = db.query(IamReadOperation.BATCH_MEMBERSHIPS, p);
    var roles = db.query(IamReadOperation.BATCH_MEMBER_ROLES, p);
    var result = new ArrayList<Map<String, Object>>();
    for (var account : accounts) {
      var item = new LinkedHashMap<>(account);
      var memberItems = new ArrayList<Map<String, Object>>();
      var roleCodes = new TreeSet<String>();
      for (var membership : memberships)
        if (membership.get("user_id").equals(account.get("id"))) {
          var m = new LinkedHashMap<String, Object>();
          m.put("groupId", membership.get("groupId"));
          m.put("groupName", membership.get("groupName"));
          var roleItems = new ArrayList<Map<String, Object>>();
          for (var role : roles)
            if (role.get("user_id").equals(account.get("id"))
                && role.get("group_id").equals(membership.get("groupId"))) {
              roleItems.add(
                  Map.of("id", role.get("id"), "code", role.get("code"), "name", role.get("name")));
              roleCodes.add(role.get("code").toString());
            }
          m.put("roles", roleItems);
          memberItems.add(m);
        }
      item.put("memberships", memberItems);
      item.put(
          "groups",
          memberItems.stream()
              .map(m -> Map.of("id", m.get("groupId"), "name", m.get("groupName")))
              .toList());
      item.put("roles", roleCodes);
      result.add(item);
    }
    return result;
  }

  private long numberOrZero(Object value) {
    return ((Number) value).longValue();
  }

  public Map<String, Object> user(long id) {
    requireSome("iam:user", "manage");
    var groups = scope("iam:user", "manage");
    var scope = auth.getScope(actor(), "iam:user", "manage");
    var p =
        Map.<String, Object>of(
            "id",
            id,
            "groups",
            groups.isEmpty() ? Set.of(-1L) : groups,
            "all",
            scope.type() == AccessScope.ScopeType.ALL);
    var rows = db.query(IamReadOperation.FIND_SCOPED_ACCOUNT, p);
    if (rows.isEmpty()) throw new AccessDeniedException("User outside managed groups");
    return enrichUsers(rows, groups).getFirst();
  }

  public Map<String, Object> groups() {
    requireSome("iam:group", "manage");
    var allowed = scope("iam:group", "manage");
    var rows =
        db.query(
            IamReadOperation.MANAGED_GROUPS,
            Map.of("groups", allowed.isEmpty() ? Set.of(-1L) : allowed));
    return Map.of("items", rows, "totalElements", rows.size());
  }

  public Map<String, Object> saveGroup(Long id, Map<String, Object> request) {
    db.lockPolicy();
    if (id == null) {
      requireGlobal();
      requireSome("iam:group", "manage");
    } else groupAccess(id, "iam:group", "manage");
    String name = text(request, "name", 100);
    if (!(request.get("enabled") instanceof Boolean enabled))
      throw new IllegalArgumentException("enabled required");
    Object before = id == null ? Map.of() : one(IamReadOperation.FIND_GROUP, Map.of("id", id));
    var p = new HashMap<String, Object>();
    p.put("name", name);
    p.put("enabled", enabled);
    p.put("id", id);
    var saved = one(id == null ? IamReadOperation.CREATE_GROUP : IamReadOperation.UPDATE_GROUP, p);
    audit(null, number(saved.get("id")), "GROUP_SAVE", before, saved);
    return saved;
  }

  public Map<String, Object> roles() {
    requireSome("iam:permission", "read");
    var rows = db.query(IamReadOperation.ROLE_SUMMARIES, Map.of());
    var permissions = db.query(IamReadOperation.ALL_ROLE_PERMISSIONS, Map.of());
    var items = new ArrayList<Map<String, Object>>();
    for (var r : rows) {
      var item = new LinkedHashMap<>(r);
      item.put(
          "permissionIds",
          permissions.stream()
              .filter(p -> p.get("role_id").equals(r.get("id")))
              .map(p -> p.get("permission_id"))
              .toList());
      items.add(item);
    }
    return Map.of("items", items);
  }

  public Map<String, Object> saveRole(Long id, Map<String, Object> request) {
    db.lockPolicy();
    requireGlobal();
    requireGlobalRolePolicy();
    return saveRoleLocked(id, request);
  }

  private Map<String, Object> saveRoleLocked(Long id, Map<String, Object> request) {
    String code = text(request, "code", 100);
    if (!code.matches("[A-Za-z0-9_:-]+")) throw new IllegalArgumentException("Invalid role code");
    String name = text(request, "name", 100);
    var permissions = ids(request.get("permissionIds"));
    for (long permission : permissions)
      one(IamReadOperation.FIND_PERMISSION, Map.of("id", permission));
    var before = id == null ? Map.of() : roleSnapshot(id);
    var p = new HashMap<String, Object>();
    p.put("id", id);
    p.put("code", code);
    p.put("name", name);
    var saved = one(id == null ? IamReadOperation.CREATE_ROLE : IamReadOperation.UPDATE_ROLE, p);
    long role = number(saved.get("id"));
    db.update(IamWriteOperation.DELETE_ROLE_PERMISSIONS, Map.of("id", role));
    for (long permission : permissions)
      db.update(
          IamWriteOperation.ADD_ROLE_PERMISSION, Map.of("role", role, "permission", permission));
    var after = roleSnapshot(role);
    audit(null, null, "ROLE_SAVE", before, after);
    return after;
  }

  private Map<String, Object> roleSnapshot(long id) {
    var role = one(IamReadOperation.FIND_ROLE, Map.of("id", id));
    role.put(
        "permissionIds",
        db.query(IamReadOperation.ROLE_PERMISSION_IDS, Map.of("id", id)).stream()
            .map(p -> p.get("permission_id"))
            .toList());
    return role;
  }

  public Map<String, Object> saveMatrix(Map<String, Object> request) {
    db.lockPolicy();
    requireGlobal();
    requireGlobalRolePolicy();
    var seen = new HashSet<Long>();
    for (var role : objects(request.get("roles"))) {
      long id = number(role.get("id"));
      if (!seen.add(id)) throw new IllegalArgumentException("Duplicate role");
      saveRoleLocked(id, role);
    }
    return Map.of("saved", true);
  }

  public Map<String, Object> deleteRole(long id) {
    db.lockPolicy();
    requireGlobal();
    requireGlobalRolePolicy();
    var before = roleSnapshot(id);
    if (!db.query(IamReadOperation.ROLE_ASSIGNMENT_EXISTS, Map.of("id", id)).isEmpty())
      throw new ApiException(ErrorCode.VERSION_CONFLICT);
    db.update(IamWriteOperation.DELETE_ROLE, Map.of("id", id));
    audit(null, null, "ROLE_DELETE", before, Map.of());
    return Map.of("saved", true);
  }

  public Map<String, Object> permissions(String resource, String action) {
    requireSome("iam:permission", "read");
    var p = new HashMap<String, Object>();
    p.put("resource", resource);
    p.put("action", action);
    return Map.of("items", db.query(IamReadOperation.SEARCH_PERMISSIONS, p));
  }

  public Map<String, Object> effective(long userId, long groupId) {
    groupAccess(groupId, "iam:user", "manage");
    if (!global()
        && db.query(IamReadOperation.MEMBERSHIP_SNAPSHOT, Map.of("user", userId, "group", groupId))
            .isEmpty()) throw new AccessDeniedException("User outside managed group");
    one(IamReadOperation.ACCOUNT_EXISTS, Map.of("id", userId));
    var p = Map.<String, Object>of("user", userId, "group", groupId);
    var rows = db.query(IamReadOperation.EFFECTIVE_PERMISSIONS, p);
    var items = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      var item = new LinkedHashMap<>(row);
      var decision =
          com.example.demo.common.authorization.PermissionDecision.evaluate(
              Boolean.TRUE.equals(row.get("user_enabled")),
              Boolean.TRUE.equals(row.get("group_enabled"))
                  || Boolean.TRUE.equals(row.get("global_admin"))
                      && "iam:group".equals(row.get("resource"))
                      && "manage".equals(row.get("action")),
              Boolean.TRUE.equals(row.get("member")),
              Boolean.TRUE.equals(row.get("global_admin")),
              Boolean.TRUE.equals(row.get("override_active")) ? (String) row.get("override") : null,
              Boolean.TRUE.equals(row.get("inherited")));
      item.put("allowed", decision.allowed());
      item.put("reason", decision.reason());
      item.put("overrideActive", row.get("override_active"));
      for (String key :
          List.of("user_enabled", "group_enabled", "member", "global_admin", "override_active"))
        item.remove(key);
      items.add(item);
    }
    return Map.of("items", items, "userId", userId, "groupId", groupId);
  }

  private Map<String, Object> decision(long user, long group, String resource, String action) {
    var results = effective(user, group);
    @SuppressWarnings("unchecked")
    var items = (List<Map<String, Object>>) results.get("items");
    for (var item : items)
      if (resource.equals(item.get("resource")) && action.equals(item.get("action")))
        return Map.of("allowed", item.get("allowed"), "reason", item.get("reason"));
    return Map.of("allowed", false, "reason", "UNKNOWN_PERMISSION");
  }

  public Map<String, Object> saveAccess(long userId, long groupId, Map<String, Object> request) {
    db.lockPolicy();
    groupAccess(groupId, "iam:user", "manage");
    var target = one(IamReadOperation.ACCOUNT_EXISTS, Map.of("id", userId));
    if (!global() && Boolean.TRUE.equals(target.get("global_admin")))
      throw new AccessDeniedException("System accounts require global administration");
    var before = accessSnapshot(userId, groupId);
    if (!global() && userId == actor())
      throw new AccessDeniedException("Self privilege changes require global administration");
    var p = Map.<String, Object>of("user", userId, "group", groupId);
    db.update(IamWriteOperation.ADD_MEMBERSHIP, p);
    if (request.containsKey("roleIds")) {
      var roleIds = ids(request.get("roleIds"));
      for (long role : roleIds) {
        one(IamReadOperation.ROLE_EXISTS, Map.of("id", role));
        if (!global()) {
          for (var permission : db.query(IamReadOperation.ROLE_CAPABILITIES, Map.of("id", role))) {
            String resource = permission.get("resource").toString();
            if (resource.startsWith("iam:")
                || !auth.canInGroup(
                    actor(), resource, permission.get("action").toString(), groupId))
              throw new AccessDeniedException(
                  "Cannot delegate administrative or unheld permissions");
          }
        }
      }
      db.update(IamWriteOperation.DELETE_MEMBER_ROLES, p);
      for (long role : roleIds)
        db.update(
            IamWriteOperation.ADD_MEMBER_ROLE,
            Map.of("user", userId, "group", groupId, "role", role));
    }
    if (request.containsKey("overrides"))
      for (var override : objects(request.get("overrides")))
        setOverride(userId, groupId, number(override.get("permissionId")), override);
    audit(userId, groupId, "MEMBER_ACCESS_SAVE", before, accessSnapshot(userId, groupId));
    return Map.of("saved", true);
  }

  private Map<String, Object> accessSnapshot(long user, long group) {
    var p = Map.<String, Object>of("user", user, "group", group);
    return Map.of(
        "membership",
        db.query(IamReadOperation.MEMBERSHIP_SNAPSHOT, p),
        "roles",
        db.query(IamReadOperation.MEMBER_ROLE_SNAPSHOT, p),
        "overrides",
        db.query(IamReadOperation.OVERRIDE_SNAPSHOT, p));
  }

  private void setOverride(long user, long group, long permission, Map<String, Object> request) {
    var definition = one(IamReadOperation.PERMISSION_CAPABILITY, Map.of("id", permission));
    if (!global()
        && (definition.get("resource").toString().startsWith("iam:")
            || !auth.canInGroup(
                actor(),
                definition.get("resource").toString(),
                definition.get("action").toString(),
                group)))
      throw new AccessDeniedException("Cannot delegate administrative or unheld permissions");
    var p = new HashMap<String, Object>();
    p.put("user", user);
    p.put("group", group);
    p.put("permission", permission);
    Object effect = request.get("effect");
    if (effect == null) {
      db.update(IamWriteOperation.DELETE_OVERRIDE, p);
      return;
    }
    if (!Set.of("ALLOW", "DENY").contains(effect))
      throw new IllegalArgumentException("Invalid effect");
    String reason = text(request, "reason", 1000);
    Instant expiry =
        request.get("expiresAt") == null ? null : parseInstant(request.get("expiresAt").toString());
    p.put("effect", effect);
    p.put("reason", reason);
    p.put("expiry", expiry == null ? null : java.sql.Timestamp.from(expiry));
    db.update(IamWriteOperation.UPSERT_OVERRIDE, p);
  }

  public Map<String, Object> saveOverride(
      long userId, long permissionId, Map<String, Object> request) {
    long group = number(request.get("groupId"));
    var override = new HashMap<>(request);
    override.put("permissionId", permissionId);
    return saveAccess(userId, group, Map.of("overrides", List.of(override)));
  }

  public Map<String, Object> removeMember(long user, long group) {
    db.lockPolicy();
    groupAccess(group, "iam:user", "manage");
    if (!global() && user == actor())
      throw new AccessDeniedException("Self privilege changes require global administration");
    var target = one(IamReadOperation.ACCOUNT_EXISTS, Map.of("id", user));
    if (!global() && Boolean.TRUE.equals(target.get("global_admin")))
      throw new AccessDeniedException("System accounts require global administration");
    var before = accessSnapshot(user, group);
    db.update(IamWriteOperation.REMOVE_MEMBERSHIP, Map.of("user", user, "group", group));
    audit(user, group, "MEMBER_REMOVE", before, Map.of());
    return Map.of("saved", true);
  }

  public Map<String, Object> check(Map<String, Object> request) {
    long user = number(request.get("userId"));
    long group = number(request.get("groupId"));
    groupAccess(group, "iam:user", "manage");
    String resource = text(request, "resource", 100), action = text(request, "action", 50);
    if (request.get("resourceId") != null) {
      long resourceId = number(request.get("resourceId"));
      if (!auth.can(user, resource, action, resourceId)
          || !auth.canInGroup(user, resource, action, group))
        return Map.of(
            "allowed",
            false,
            "reason",
            "RESOURCE_ACCESS_DENIED"); // additionally enforce the actual resolver group via a
      // scoped resolver check below
      if (!auth.isResourceInGroup(resource, resourceId, group))
        return Map.of("allowed", false, "reason", "RESOURCE_GROUP_MISMATCH");
    }
    return decision(user, group, resource, action);
  }

  public Map<String, Object> audit(
      Long actorId, Long userId, String from, String to, int page, int size) {
    page(page, size);
    requireSome("iam:audit", "read");
    var allowed = scope("iam:audit", "read");
    var p = new HashMap<String, Object>();
    p.put("groups", allowed.isEmpty() ? Set.of(-1L) : allowed);
    p.put("all", auth.getScope(actor(), "iam:audit", "read").type() == AccessScope.ScopeType.ALL);
    p.put("actor", actorId);
    p.put("user", userId);
    p.put("from", from == null ? null : java.sql.Timestamp.from(parseInstant(from)));
    p.put("to", to == null ? null : java.sql.Timestamp.from(parseInstant(to)));
    p.put("limit", size);
    p.put("offset", (long) page * size);
    var rows = db.query(IamReadOperation.SEARCH_AUDIT, p);
    var items = new ArrayList<Map<String, Object>>();
    for (var row : rows) {
      var item = new LinkedHashMap<>(row);
      try {
        item.put("before", mapper.readTree(row.get("before").toString()));
        item.put("after", mapper.readTree(row.get("after").toString()));
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new IllegalStateException(e);
      }
      items.add(item);
    }
    return Map.of(
        "items",
        items,
        "totalElements",
        one(IamReadOperation.COUNT_AUDIT, p).get("n"),
        "page",
        page,
        "size",
        size);
  }
}
