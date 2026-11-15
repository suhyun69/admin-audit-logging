package com.example.demo.usecase.impl;

import com.example.demo.common.authorization.AccessScope;
import com.example.demo.common.authorization.AuthorizationService;
import com.example.demo.common.authorization.PermissionDecision;
import com.example.demo.common.authorization.ResourceResolver;
import com.example.demo.iam.application.port.out.IamRepositoryPort;
import com.example.demo.iam.application.query.IamReadOperation;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AuthorizationEngineUseCaseImpl implements AuthorizationService {
  private final IamRepositoryPort repository;
  private final Map<String, ResourceResolver> resolvers;

  public AuthorizationEngineUseCaseImpl(
      IamRepositoryPort repository, List<ResourceResolver> resolvers) {
    this.repository = repository;
    var map = new HashMap<String, ResourceResolver>();
    for (var r : resolvers) {
      if (map.put(r.supports(), r) != null)
        throw new IllegalStateException("Duplicate resource resolver");
    }
    this.resolvers = Map.copyOf(map);
  }

  public boolean isResourceInGroup(String resource, Long id, Long group) {
    var r = resource == null ? null : resolvers.get(resource);
    return r != null && id != null && group != null && group.equals(r.resolveGroupId(id));
  }

  public void lockPolicy() {
    repository.lockPolicy();
  }

  public void lockResource(String resource, Long id) {
    var r = resource == null ? null : resolvers.get(resource);
    if (r == null)
      throw new org.springframework.security.access.AccessDeniedException("Unknown resource");
    r.lock(id);
  }

  public boolean can(Long user, String resource, String action, Long id) {
    if (user == null || id == null || user <= 0 || id <= 0) return false;
    var r = resource == null ? null : resolvers.get(resource);
    if (r == null) return false;
    Long group = r.resolveGroupId(id);
    return group != null && canInGroup(user, resource, action, group);
  }

  public boolean canInGroup(Long user, String resource, String action, Long group) {
    if (group == null) return false;
    var s = getScope(user, resource, action);
    return s.type() == AccessScope.ScopeType.ALL
        ? activeGroups(resource.equals("iam:group") && action.equals("manage")).contains(group)
        : s.groupIds().contains(group);
  }

  public AccessScope getScope(Long user, String resource, String action) {
    if (user == null || user <= 0 || resource == null || action == null) return AccessScope.none();
    var account = repository.query(IamReadOperation.ENABLED_POLICY_ACCOUNT, Map.of("id", user));
    if (account.isEmpty()) return AccessScope.none();
    var permission =
        repository.query(IamReadOperation.POLICY_PERMISSION, Map.of("r", resource, "a", action));
    if (permission.isEmpty()) return AccessScope.none();
    boolean global = Boolean.TRUE.equals(account.getFirst().get("global_admin"));
    boolean lifecycle = global && resource.equals("iam:group") && action.equals("manage");
    var p =
        Map.<String, Object>of(
            "user",
            user,
            "permission",
            permission.getFirst().get("id"),
            "includeInactive",
            lifecycle || global && resource.equals("iam:role") && action.equals("manage"));
    var rows = repository.query(IamReadOperation.GROUP_POLICY, p);
    boolean denied = false;
    var groups = new HashSet<Long>();
    for (var row : rows) {
      if ("DENY".equals(row.get("effect"))) {
        denied = true;
        continue;
      }
      if (PermissionDecision.evaluate(
              true,
              Boolean.TRUE.equals(row.get("enabled")) || lifecycle,
              Boolean.TRUE.equals(row.get("member")),
              global,
              (String) row.get("effect"),
              Boolean.TRUE.equals(row.get("inherited")))
          .allowed()) groups.add(((Number) row.get("id")).longValue());
    }
    if (global && !denied) return new AccessScope(AccessScope.ScopeType.ALL, Set.of());
    return groups.isEmpty()
        ? AccessScope.none()
        : new AccessScope(AccessScope.ScopeType.GROUPS, groups);
  }

  private Set<Long> activeGroups(boolean includeInactive) {
    var result = new HashSet<Long>();
    for (var row :
        repository.query(
            IamReadOperation.ACTIVE_POLICY_GROUP_IDS, Map.of("includeInactive", includeInactive)))
      result.add(((Number) row.get("id")).longValue());
    return result;
  }

  public Map<Long, Set<String>> getAllowedActions(
      Long user, String resource, Collection<Long> ids, Set<String> actions) {
    if (ids == null
        || actions == null
        || ids.stream().anyMatch(Objects::isNull)
        || actions.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Invalid permission batch");
    if (ids.size() > 1000) throw new IllegalArgumentException("Too many resources");
    var result = new LinkedHashMap<Long, Set<String>>();
    for (Long id : ids) result.put(id, new HashSet<>());
    var resolver = resource == null ? null : resolvers.get(resource);
    if (resolver == null || ids.isEmpty()) return result;
    var groups = resolver.resolveGroupIds(ids);
    var active = activeGroups(false);
    for (var action : actions) {
      var scope = getScope(user, resource, action);
      for (var e : groups.entrySet())
        if (scope.type() == AccessScope.ScopeType.ALL
            ? active.contains(e.getValue())
            : scope.groupIds().contains(e.getValue())) result.get(e.getKey()).add(action);
    }
    result.replaceAll((id, set) -> Set.copyOf(set));
    return result;
  }
}
