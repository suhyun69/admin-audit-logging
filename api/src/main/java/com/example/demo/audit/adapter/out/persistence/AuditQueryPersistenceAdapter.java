package com.example.demo.audit.adapter.out.persistence;

import com.example.demo.audit.application.port.out.AuditQueryPort;
import com.example.demo.audit.domain.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Repository
public class AuditQueryPersistenceAdapter implements AuditQueryPort {
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditRowMapper rows;
    public AuditQueryPersistenceAdapter(NamedParameterJdbcTemplate jdbc,AuditRowMapper rows) { this.jdbc=jdbc;this.rows=rows; }
    @Override public List<AuditEvent> search(AuditQuery query) {
        var sql=new StringBuilder("SELECT *,host(ip_address) AS ip_host FROM audit_logs WHERE occurred_at>=:from AND occurred_at<:to");
        var params=new MapSqlParameterSource().addValue("from",boundary(query.from())).addValue("to",boundary(query.to())).addValue("limit",query.limit());
        if(query.actorId()!=null) { sql.append(" AND actor_id=:actor");params.addValue("actor",query.actorId()); }
        if(query.eventType()!=null) { sql.append(" AND event_type=:type");params.addValue("type",query.eventType().name()); }
        if(query.result()!=null) { sql.append(" AND result=:result");params.addValue("result",query.result().name()); }
        if(query.resourceType()!=null) { sql.append(" AND resource_type=:resourceType");params.addValue("resourceType",query.resourceType()); }
        if(query.resourceId()!=null) { sql.append(" AND resource_id=:resourceId");params.addValue("resourceId",query.resourceId()); }
        if(query.interactionId()!=null) { sql.append(" AND interaction_id=:interaction");params.addValue("interaction",query.interactionId()); }
        if(query.requestId()!=null) { sql.append(" AND request_id=:request");params.addValue("request",query.requestId()); }
        if(query.cursor()!=null) {
            sql.append(" AND (occurred_at,id)<(:cursorTime,:cursorId)");params.addValue("cursorTime",Timestamp.from(query.cursor().occurredAt())).addValue("cursorId",query.cursor().id());
        }
        sql.append(" ORDER BY occurred_at DESC,id DESC LIMIT :limit");
        return jdbc.query(sql.toString(),params,rows);
    }
    @Override public Optional<AuditEvent> detail(UUID id) {
        return jdbc.query("SELECT *,host(ip_address) AS ip_host FROM audit_logs WHERE id=:id",new MapSqlParameterSource("id",id),rows).stream().findFirst();
    }
    @Override public List<AuditEvent> interaction(UUID id,int limit,AuditPosition cursor) {
        var sql=new StringBuilder("SELECT *,host(ip_address) AS ip_host FROM audit_logs WHERE interaction_id=:interaction");
        var params=new MapSqlParameterSource("interaction",id).addValue("limit",limit);
        if(cursor!=null) {
            sql.append(" AND (occurred_at,id)>(:time,:id)");params.addValue("time",Timestamp.from(cursor.occurredAt())).addValue("id",cursor.id());
        }
        sql.append(" ORDER BY occurred_at ASC,id ASC LIMIT :limit");return jdbc.query(sql.toString(),params,rows);
    }
    @Override public Map<UUID,List<AuditChange>> changes(List<UUID> ids) {
        if(ids.isEmpty())return Map.of();
        var result=new LinkedHashMap<UUID,List<AuditChange>>();
        jdbc.query("""
                SELECT audit_log_id,field_name,before_value #>> '{}' before_text,after_value #>> '{}' after_text
                FROM audit_log_changes WHERE audit_log_id IN (:ids) AND field_name='status'
                AND before_value IN ('"ACTIVE"'::jsonb,'"SUSPENDED"'::jsonb) AND after_value IN ('"ACTIVE"'::jsonb,'"SUSPENDED"'::jsonb)
                ORDER BY id
                """,new MapSqlParameterSource("ids",ids),(org.springframework.jdbc.core.RowCallbackHandler)rs->{
                    result.computeIfAbsent(rs.getObject("audit_log_id",UUID.class),key->new ArrayList<>())
                            .add(new AuditChange(rs.getString("field_name"),rs.getString("before_text"),rs.getString("after_text")));
                });
        result.replaceAll((id,changes)->List.copyOf(changes));return Map.copyOf(result);
    }
    @Override public AuditSummary summary(Long actorId,Instant from,Instant to) {
        var sql=new StringBuilder("""
                SELECT count(*) total,count(*) FILTER (WHERE result='DENIED') denied,
                count(*) FILTER (WHERE result='FAILURE' OR status_code>=500) errors
                FROM audit_logs WHERE occurred_at>=:from AND occurred_at<:to
                """);
        var params=new MapSqlParameterSource().addValue("from",boundary(from)).addValue("to",boundary(to));
        if(actorId!=null) { sql.append(" AND actor_id=:actor");params.addValue("actor",actorId); }
        return jdbc.queryForObject(sql.toString(),params,(rs,n)->new AuditSummary(rs.getLong("total"),rs.getLong("denied"),rs.getLong("errors")));
    }
    // PostgreSQL timestamps are discrete microseconds. Ceil both boundaries to preserve >= and < for nanosecond inputs.
    private Timestamp boundary(Instant value) {
        int remainder=value.getNano()%1000;
        return Timestamp.from(remainder==0?value:value.plusNanos(1000-remainder));
    }
}
