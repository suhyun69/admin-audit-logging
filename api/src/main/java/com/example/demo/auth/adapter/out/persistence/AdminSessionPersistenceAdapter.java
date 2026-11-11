package com.example.demo.auth.adapter.out.persistence;

import com.example.demo.auth.application.port.out.AdminSessionPort;
import com.example.demo.auth.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AdminSessionPersistenceAdapter implements AdminSessionPort {
    private final JdbcTemplate jdbc;
    public AdminSessionPersistenceAdapter(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Optional<AdminAccount> findAccount(String username) {
        return jdbc.query("SELECT * FROM admin_users WHERE username=?", (rs,n) -> account(rs),username).stream().findFirst();
    }
    @Override public java.util.List<AdminAccount> enabledAccounts() {
        return jdbc.query("SELECT id,username,'' AS password_hash,display_name,role,enabled FROM admin_users WHERE enabled=TRUE ORDER BY id",(rs,n)->account(rs));
    }
    @Override public void createSession(AdminSession session) {
        jdbc.update("INSERT INTO admin_sessions(id,admin_user_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?)",
                session.id(),session.adminUserId(),session.tokenHash(),Timestamp.from(session.createdAt()),Timestamp.from(session.expiresAt()));
    }
    @Override public Optional<AuthenticatedSession> findActiveSession(String hash, Instant now) {
        return jdbc.query("""
                SELECT a.*,s.id session_id,s.token_hash,s.created_at session_created_at,s.expires_at,s.revoked_at
                FROM admin_sessions s JOIN admin_users a ON a.id=s.admin_user_id
                WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>? AND a.enabled=TRUE
                """,(rs,n) -> new AuthenticatedSession(account(rs),new AdminSession(rs.getObject("session_id",UUID.class),
                rs.getLong("id"),rs.getString("token_hash"),rs.getTimestamp("session_created_at").toInstant(),
                rs.getTimestamp("expires_at").toInstant(),null)),hash,Timestamp.from(now)).stream().findFirst();
    }
    @Override public void revoke(UUID id, Instant now) { jdbc.update("UPDATE admin_sessions SET revoked_at=? WHERE id=? AND revoked_at IS NULL",Timestamp.from(now),id); }
    private AdminAccount account(ResultSet rs) throws SQLException {
        return new AdminAccount(rs.getLong("id"),rs.getString("username"),rs.getString("password_hash"),rs.getString("display_name"),AdminRole.valueOf(rs.getString("role")),rs.getBoolean("enabled"));
    }
}
