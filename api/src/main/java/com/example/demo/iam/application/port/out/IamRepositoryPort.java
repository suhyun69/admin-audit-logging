package com.example.demo.iam.application.port.out;

import com.example.demo.iam.application.query.IamReadOperation;
import com.example.demo.iam.application.query.IamWriteOperation;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface IamRepositoryPort {
  List<Map<String, Object>> query(IamReadOperation operation, Map<String, ?> parameters);

  int update(IamWriteOperation operation, Map<String, ?> parameters);

  void lockPolicy();

  void advancePolicy();

  void audit(
      long actorId,
      Long targetUserId,
      Long groupId,
      String type,
      Object before,
      Object after,
      UUID requestId);
}
