package com.example.demo.controller;

import com.example.demo.common.authorization.PermissionMode;
import com.example.demo.common.authorization.RequirePermission;
import com.example.demo.usecase.IamManagementUseCase;
import java.util.Map;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/iam")
public class IamController {
  private final IamManagementUseCase iam;

  public IamController(IamManagementUseCase iam) {
    this.iam = iam;
  }

  @GetMapping("/me")
  public Map<String, Object> me() {
    return iam.me();
  }

  @GetMapping("/users")
  @RequirePermission(resource = "iam:user", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> users(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Boolean enabled,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return iam.users(q, enabled, page, size);
  }

  @GetMapping("/users/{id}")
  @RequirePermission(resource = "iam:user", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> user(@PathVariable long id) {
    return iam.user(id);
  }

  @GetMapping("/groups")
  @RequirePermission(resource = "iam:group", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> groups() {
    return iam.groups();
  }

  @PostMapping("/groups")
  @RequirePermission(resource = "iam:group", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> createGroup(@RequestBody Map<String, Object> request) {
    return iam.saveGroup(null, request);
  }

  @PutMapping("/groups/{id}")
  @RequirePermission(resource = "iam:group", action = "manage", groupId = "#id")
  public Map<String, Object> saveGroup(
      @PathVariable long id, @RequestBody Map<String, Object> request) {
    return iam.saveGroup(id, request);
  }

  @GetMapping("/roles")
  @RequirePermission(resource = "iam:permission", action = "read", mode = PermissionMode.SCOPE)
  public Map<String, Object> roles() {
    return iam.roles();
  }

  @PostMapping("/roles")
  @RequirePermission(resource = "iam:role", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> createRole(@RequestBody Map<String, Object> request) {
    return iam.saveRole(null, request);
  }

  @PutMapping("/roles/matrix")
  @RequirePermission(resource = "iam:role", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> matrix(@RequestBody Map<String, Object> request) {
    return iam.saveMatrix(request);
  }

  @PutMapping("/roles/{id}")
  @RequirePermission(resource = "iam:role", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> saveRole(
      @PathVariable long id, @RequestBody Map<String, Object> request) {
    return iam.saveRole(id, request);
  }

  @DeleteMapping("/roles/{id}")
  @RequirePermission(resource = "iam:role", action = "manage", mode = PermissionMode.SCOPE)
  public Map<String, Object> deleteRole(@PathVariable long id) {
    return iam.deleteRole(id);
  }

  @GetMapping("/permissions")
  @RequirePermission(resource = "iam:permission", action = "read", mode = PermissionMode.SCOPE)
  public Map<String, Object> permissions(
      @RequestParam(required = false) String resource,
      @RequestParam(required = false) String action) {
    return iam.permissions(resource, action);
  }

  @GetMapping("/users/{id}/effective-permissions")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#groupId")
  public Map<String, Object> effective(@PathVariable long id, @RequestParam long groupId) {
    return iam.effective(id, groupId);
  }

  @PutMapping("/users/{userId}/groups/{groupId}/access")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#groupId")
  public Map<String, Object> access(
      @PathVariable long userId,
      @PathVariable long groupId,
      @RequestBody Map<String, Object> request) {
    return iam.saveAccess(userId, groupId, request);
  }

  @PutMapping("/groups/{groupId}/members/{userId}")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#groupId")
  public Map<String, Object> member(
      @PathVariable long userId,
      @PathVariable long groupId,
      @RequestBody Map<String, Object> request) {
    return iam.saveAccess(userId, groupId, request);
  }

  @DeleteMapping("/groups/{groupId}/members/{userId}")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#groupId")
  public Map<String, Object> remove(@PathVariable long userId, @PathVariable long groupId) {
    return iam.removeMember(userId, groupId);
  }

  @PutMapping("/users/{id}/overrides/{permissionId}")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#request['groupId']")
  public Map<String, Object> override(
      @PathVariable long id,
      @PathVariable long permissionId,
      @RequestBody Map<String, Object> request) {
    return iam.saveOverride(id, permissionId, request);
  }

  @PostMapping("/check")
  @RequirePermission(resource = "iam:user", action = "manage", groupId = "#request['groupId']")
  public Map<String, Object> check(@RequestBody Map<String, Object> request) {
    return iam.check(request);
  }

  @GetMapping("/audit-logs")
  @RequirePermission(resource = "iam:audit", action = "read", mode = PermissionMode.SCOPE)
  public Map<String, Object> audit(
      @RequestParam(required = false) Long actorId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return iam.audit(actorId, userId, from, to, page, size);
  }
}
