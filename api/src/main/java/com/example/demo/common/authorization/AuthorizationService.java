package com.example.demo.common.authorization;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public interface AuthorizationService {
  boolean can(Long userId, String resource, String action, Long resourceId);

  boolean canInGroup(Long userId, String resource, String action, Long groupId);

  AccessScope getScope(Long userId, String resource, String action);

  Map<Long, Set<String>> getAllowedActions(
      Long userId, String resource, Collection<Long> resourceIds, Set<String> actions);

  boolean isResourceInGroup(String resource, Long resourceId, Long groupId);

  void lockPolicy();

  void lockResource(String resource, Long resourceId);
}
