package com.example.demo.usecase;

import java.util.Map;

public interface IamManagementUseCase {
  Map<String, Object> me();

  Map<String, Object> users(String q, Boolean enabled, int page, int size);

  Map<String, Object> user(long id);

  Map<String, Object> groups();

  Map<String, Object> saveGroup(Long id, Map<String, Object> request);

  Map<String, Object> roles();

  Map<String, Object> saveRole(Long id, Map<String, Object> request);

  Map<String, Object> saveMatrix(Map<String, Object> request);

  Map<String, Object> deleteRole(long id);

  Map<String, Object> permissions(String resource, String action);

  Map<String, Object> effective(long userId, long groupId);

  Map<String, Object> saveAccess(long userId, long groupId, Map<String, Object> request);

  Map<String, Object> saveOverride(long userId, long permissionId, Map<String, Object> request);

  Map<String, Object> removeMember(long userId, long groupId);

  Map<String, Object> check(Map<String, Object> request);

  Map<String, Object> audit(Long actorId, Long userId, String from, String to, int page, int size);
}
