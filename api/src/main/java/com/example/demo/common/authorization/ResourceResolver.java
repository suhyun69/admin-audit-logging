package com.example.demo.common.authorization;

import java.util.Collection;
import java.util.Map;

public interface ResourceResolver {
  String supports();

  Long resolveGroupId(Long resourceId);

  /** Every implementation must resolve all IDs with one database query. */
  Map<Long, Long> resolveGroupIds(Collection<Long> resourceIds);

  void lock(Long resourceId);
}
