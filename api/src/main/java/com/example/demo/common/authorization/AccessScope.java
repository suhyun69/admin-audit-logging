package com.example.demo.common.authorization;

import java.util.Set;

/** ALL means all active groups under an explicit global policy; never inactive groups. */
public record AccessScope(ScopeType type, Set<Long> groupIds) {
  public enum ScopeType {
    NONE,
    GROUPS,
    ALL
  }

  public AccessScope {
    groupIds = Set.copyOf(groupIds);
  }

  public static AccessScope none() {
    return new AccessScope(ScopeType.NONE, Set.of());
  }
}
