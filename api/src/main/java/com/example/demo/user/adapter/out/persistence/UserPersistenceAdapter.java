package com.example.demo.user.adapter.out.persistence;

import com.example.demo.user.application.port.out.UserRepositoryPort;
import com.example.demo.user.domain.*;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

@Repository
public class UserPersistenceAdapter implements UserRepositoryPort {
    private final NamedParameterJdbcTemplate jdbc;
    public UserPersistenceAdapter(NamedParameterJdbcTemplate jdbc) { this.jdbc=jdbc; }
    @Override public List<User> find(String search,UserStatus status,int limit,Long afterId) {
        var sql=new StringBuilder("SELECT * FROM users WHERE 1=1");
        var params=new MapSqlParameterSource().addValue("limit",limit);
        if(search!=null&&!search.isEmpty()) {
            sql.append(" AND (email ILIKE :search ESCAPE '!' OR name ILIKE :search ESCAPE '!')");
            params.addValue("search","%"+search.replace("!","!!").replace("%","!%").replace("_","!_")+"%");
        }
        if(status!=null) { sql.append(" AND status=:status");params.addValue("status",status.name()); }
        if(afterId!=null) { sql.append(" AND id>:afterId");params.addValue("afterId",afterId); }
        sql.append(" ORDER BY id ASC LIMIT :limit");
        return jdbc.query(sql.toString(),params,(rs,n)->map(rs));
    }
    @Override public Optional<User> findById(long id) {
        return jdbc.query("SELECT * FROM users WHERE id=:id",new MapSqlParameterSource("id",id),(rs,n)->map(rs)).stream().findFirst();
    }
    private User map(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"),rs.getString("email"),rs.getString("name"),UserStatus.valueOf(rs.getString("status")),rs.getLong("version"),
                rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());
    }
}
