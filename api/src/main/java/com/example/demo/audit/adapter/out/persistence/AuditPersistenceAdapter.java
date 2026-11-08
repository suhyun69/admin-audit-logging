package com.example.demo.audit.adapter.out.persistence;

import com.example.demo.audit.application.port.out.AuditWritePort;
import com.example.demo.audit.domain.AuditEvent;
import com.example.demo.audit.domain.AuditChange;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.List;

@Repository
public class AuditPersistenceAdapter implements AuditWritePort {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public AuditPersistenceAdapter(NamedParameterJdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    @Override public void insert(AuditEvent event, List<AuditChange> changes) {
        var parameters = new MapSqlParameterSource()
                .addValue("id",event.id()).addValue("occurred",Timestamp.from(event.occurredAt())).addValue("received",Timestamp.from(event.receivedAt()))
                .addValue("type",event.eventType().name()).addValue("action",event.action()).addValue("actor",event.actorId())
                .addValue("actorName",event.actorName()).addValue("actorRole",event.actorRole()).addValue("session",event.sessionId())
                .addValue("interaction",event.interactionId()).addValue("request",event.requestId()).addValue("source",event.source().name())
                .addValue("resourceType",event.resourceType()).addValue("resourceId",event.resourceId()).addValue("method",event.httpMethod())
                .addValue("path",event.requestPath()).addValue("status",event.statusCode()).addValue("duration",event.durationMs())
                .addValue("result",event.result().name()).addValue("ip",event.ipAddress()).addValue("agent",event.userAgent())
                .addValue("error",event.errorCode()).addValue("metadata",json(event.metadata()));
        jdbc.update("""
                INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,session_id,
                interaction_id,request_id,source,resource_type,resource_id,http_method,request_path,status_code,duration_ms,
                result,ip_address,user_agent,error_code,metadata)
                VALUES (:id,:occurred,:received,:type,:action,:actor,:actorName,:actorRole,:session,:interaction,:request,:source,
                :resourceType,:resourceId,:method,:path,:status,:duration,:result,CAST(:ip AS inet),:agent,:error,CAST(:metadata AS jsonb))
                """, parameters);
        for (AuditChange change : changes) {
            jdbc.update("INSERT INTO audit_log_changes(audit_log_id,field_name,before_value,after_value) VALUES (:id,:field,CAST(:before AS jsonb),CAST(:after AS jsonb))",
                    new MapSqlParameterSource().addValue("id",event.id()).addValue("field",change.fieldName())
                            .addValue("before",json(change.beforeValue())).addValue("after",json(change.afterValue())));
        }
    }
    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("Invalid audit data"); }
    }
}
