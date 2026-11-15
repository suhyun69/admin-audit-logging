package com.example.demo.iam.adapter.out.persistence;

import com.example.demo.common.authorization.ResourceResolver;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class UserResourceResolver implements ResourceResolver {
  private final NamedParameterJdbcTemplate jdbc;

  public UserResourceResolver(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public String supports() {
    return "user";
  }

  public Long resolveGroupId(Long id) {
    return resolveGroupIds(List.of(id)).get(id);
  }

  public Map<Long, Long> resolveGroupIds(Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var result = new HashMap<Long, Long>();
    jdbc.query(
        "SELECT id,group_id FROM users WHERE id IN (:ids)",
        Map.of("ids", ids),
        rs -> {
          result.put(rs.getLong("id"), rs.getLong("group_id"));
        });
    return result;
  }

  public void lock(Long id) {
    jdbc.queryForList("SELECT id FROM users WHERE id=:id FOR UPDATE", Map.of("id", id));
  }
}
