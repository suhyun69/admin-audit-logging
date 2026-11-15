package com.example.demo.user.adapter.out.persistence;

import com.example.demo.user.application.port.out.UserRepositoryPort;
import com.example.demo.user.domain.User;
import com.example.demo.user.domain.UserStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserPersistenceAdapter implements UserRepositoryPort {
  private final NamedParameterJdbcTemplate jdbc;

  public UserPersistenceAdapter(NamedParameterJdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public List<User> find(String search, UserStatus status, int limit, Long afterId) {
    var sql = new StringBuilder("SELECT * FROM users WHERE 1=1");
    var params = new MapSqlParameterSource().addValue("limit", limit);
    if (search != null && !search.isEmpty()) {
      sql.append(" AND (email ILIKE :search ESCAPE '!' OR name ILIKE :search ESCAPE '!')");
      params.addValue(
          "search", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (status != null) {
      sql.append(" AND status=:status");
      params.addValue("status", status.name());
    }
    if (afterId != null) {
      sql.append(" AND id>:afterId");
      params.addValue("afterId", afterId);
    }
    sql.append(" ORDER BY id ASC LIMIT :limit");
    return jdbc.query(sql.toString(), params, (rs, n) -> map(rs));
  }

  @Override
  public List<User> findScoped(
      String search,
      UserStatus status,
      int limit,
      Long afterId,
      boolean all,
      java.util.Set<Long> groups) {
    if (!all && groups.isEmpty()) return List.of();
    var params =
        new MapSqlParameterSource()
            .addValue("all", all)
            .addValue("groups", groups.isEmpty() ? java.util.Set.of(-1L) : groups)
            .addValue("limit", limit);
    String sql = scopedWhere(search, status, params);
    if (afterId != null) {
      sql += " AND id>:afterId";
      params.addValue("afterId", afterId);
    }
    return jdbc.query(
        "SELECT * FROM users WHERE " + sql + " ORDER BY id ASC LIMIT :limit",
        params,
        (rs, n) -> map(rs));
  }

  @Override
  public long countScoped(
      String search, UserStatus status, boolean all, java.util.Set<Long> groups) {
    if (!all && groups.isEmpty()) return 0;
    var params =
        new MapSqlParameterSource()
            .addValue("all", all)
            .addValue("groups", groups.isEmpty() ? java.util.Set.of(-1L) : groups);
    return jdbc.queryForObject(
        "SELECT count(*) FROM users WHERE " + scopedWhere(search, status, params),
        params,
        Long.class);
  }

  private String scopedWhere(String search, UserStatus status, MapSqlParameterSource params) {
    String sql =
        "EXISTS(SELECT 1 FROM iam_groups g WHERE g.id=users.group_id AND g.enabled=TRUE) AND(:all OR group_id IN(:groups))";
    if (search != null && !search.isEmpty()) {
      sql += " AND(email ILIKE :search ESCAPE '!' OR name ILIKE :search ESCAPE '!')";
      params.addValue(
          "search", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }
    if (status != null) {
      sql += " AND status=:status";
      params.addValue("status", status.name());
    }
    return sql;
  }

  @Override
  public Optional<User> findById(long id) {
    return jdbc
        .query(
            "SELECT * FROM users WHERE id=:id",
            new MapSqlParameterSource("id", id),
            (rs, n) -> map(rs))
        .stream()
        .findFirst();
  }

  @Override
  public boolean changeStatus(long id, long expectedVersion, UserStatus target) {
    return jdbc.update(
            "UPDATE users SET status=:status,version=version+1,updated_at=now() WHERE id=:id AND version=:version",
            new MapSqlParameterSource()
                .addValue("id", id)
                .addValue("version", expectedVersion)
                .addValue("status", target.name()))
        == 1;
  }

  private User map(ResultSet rs) throws SQLException {
    return new User(
        rs.getLong("id"),
        rs.getString("email"),
        rs.getString("name"),
        UserStatus.valueOf(rs.getString("status")),
        rs.getLong("version"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getLong("group_id"));
  }
}
