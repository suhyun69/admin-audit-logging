package com.example.demo.audit.adapter.out.persistence;

import com.example.demo.audit.application.port.out.ClientAuditPort;
import com.example.demo.audit.domain.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClientAuditPersistenceAdapter implements ClientAuditPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final org.springframework.context.ApplicationEventPublisher events;
    public ClientAuditPersistenceAdapter(NamedParameterJdbcTemplate jdbc,ObjectMapper mapper,org.springframework.context.ApplicationEventPublisher events) { this.jdbc=jdbc;this.mapper=mapper;this.events=events; }
    @Override public Optional<ClientEventIdentity> identity(UUID id) {
        return jdbc.query("SELECT actor_id,source,metadata->>'payloadDigest' digest FROM audit_logs WHERE id=:id",new MapSqlParameterSource("id",id),
                (rs,n)->new ClientEventIdentity((Long)rs.getObject("actor_id"),AuditSource.valueOf(rs.getString("source")),rs.getString("digest"))).stream().findFirst();
    }
    @Override public boolean insertOnce(AuditEvent event) {
        String metadata;
        try { metadata=mapper.writeValueAsString(event.metadata()); }catch(JsonProcessingException exception){throw new IllegalArgumentException("Invalid client metadata");}
        var params=new MapSqlParameterSource().addValue("id",event.id()).addValue("occurred",Timestamp.from(event.occurredAt())).addValue("received",Timestamp.from(event.receivedAt()))
                .addValue("type",event.eventType().name()).addValue("action",event.action()).addValue("actor",event.actorId()).addValue("name",event.actorName()).addValue("role",event.actorRole())
                .addValue("session",event.sessionId()).addValue("interaction",event.interactionId()).addValue("request",event.requestId())
                .addValue("resourceType",event.resourceType()).addValue("resourceId",event.resourceId()).addValue("metadata",metadata);
        boolean inserted=jdbc.update("""
                INSERT INTO audit_logs(id,occurred_at,received_at,event_type,action,actor_id,actor_name,actor_role,session_id,
                interaction_id,request_id,source,resource_type,resource_id,result,metadata)
                VALUES (:id,:occurred,:received,:type,:action,:actor,:name,:role,:session,:interaction,:request,'CLIENT',:resourceType,:resourceId,'ATTEMPTED',CAST(:metadata AS jsonb))
                ON CONFLICT(id) DO NOTHING
                """,params)==1;
        if(inserted) events.publishEvent(event);
        return inserted;
    }
}
