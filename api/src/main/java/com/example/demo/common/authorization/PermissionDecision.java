package com.example.demo.common.authorization;

public record PermissionDecision(boolean allowed, String reason) {
  public static PermissionDecision evaluate(
      boolean userEnabled,
      boolean groupEnabled,
      boolean member,
      boolean global,
      String effect,
      boolean inherited) {
    if (!userEnabled) return new PermissionDecision(false, "ACCOUNT_DISABLED");
    if (!groupEnabled) return new PermissionDecision(false, "GROUP_DISABLED");
    if (!member && !global) return new PermissionDecision(false, "NOT_A_GROUP_MEMBER");
    if ("DENY".equals(effect)) return new PermissionDecision(false, "EXPLICIT_DENY");
    if ("ALLOW".equals(effect)) return new PermissionDecision(true, "EXPLICIT_ALLOW");
    if (global) return new PermissionDecision(true, "GLOBAL_POLICY_ALLOW");
    return new PermissionDecision(inherited, inherited ? "ROLE_ALLOW" : "DEFAULT_DENY");
  }
}
