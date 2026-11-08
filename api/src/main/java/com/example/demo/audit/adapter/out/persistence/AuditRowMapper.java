package com.example.demo.audit.adapter.out.persistence;

import com.example.demo.audit.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;

@Component
public class AuditRowMapper implements RowMapper<AuditEvent> {
    private final ObjectMapper mapper;
    public AuditRowMapper(ObjectMapper mapper) { this.mapper=mapper; }
    @Override public AuditEvent mapRow(ResultSet rs,int row) throws SQLException {
        Map<String,Object> metadata;
        try { metadata=mapper.readValue(rs.getString("metadata"),new TypeReference<Map<String,Object>>(){}); }
        catch(java.io.IOException exception) { throw new SQLException("Invalid stored audit metadata"); }
        return new AuditEvent(rs.getObject("id",UUID.class),rs.getTimestamp("occurred_at").toInstant(),rs.getTimestamp("received_at").toInstant(),
                AuditEventType.valueOf(rs.getString("event_type")),rs.getString("action"),(Long)rs.getObject("actor_id"),rs.getString("actor_name"),rs.getString("actor_role"),
                rs.getObject("session_id",UUID.class),rs.getObject("interaction_id",UUID.class),rs.getObject("request_id",UUID.class),AuditSource.valueOf(rs.getString("source")),
                rs.getString("resource_type"),rs.getString("resource_id"),rs.getString("http_method"),rs.getString("request_path"),(Integer)rs.getObject("status_code"),
                (Long)rs.getObject("duration_ms"),AuditResult.valueOf(rs.getString("result")),rs.getString("ip_host"),rs.getString("user_agent"),rs.getString("error_code"),metadata);
    }
}
