package com.example.demo.common.page;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.Map;
import java.time.Instant;
import java.util.UUID;

@Component
public class CursorCodec {
    private final ObjectMapper strict;
    public CursorCodec(ObjectMapper mapper) { strict=mapper.copy().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION); }
    public String user(long id) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(strict.writeValueAsBytes(Map.of("id",id))); }
        catch(JsonProcessingException exception) { throw new IllegalStateException("Cursor encoding failed"); }
    }
    public Long user(String encoded) {
        if(encoded==null)return null;
        try {
            if(encoded.length()>512||!encoded.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(encoded);
            if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded))throw new IllegalArgumentException();
            var node=strict.readTree(bytes);
            if(node==null||!node.isObject()||node.size()!=1||!node.has("id")||!node.get("id").isIntegralNumber()
                    ||!node.get("id").canConvertToLong()||node.get("id").longValue()<=0)throw new IllegalArgumentException();
            return node.get("id").longValue();
        } catch(java.io.IOException|IllegalArgumentException exception) { throw new ApiException(ErrorCode.INVALID_CURSOR); }
    }
    public String audit(Instant occurredAt,UUID id) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(strict.writeValueAsBytes(Map.of("occurredAt",occurredAt.toString(),"id",id.toString()))); }
        catch(JsonProcessingException exception) { throw new IllegalStateException("Cursor encoding failed"); }
    }
    public AuditCursorPosition audit(String encoded) {
        if(encoded==null)return null;
        try {
            if(encoded.length()>512||!encoded.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException();
            byte[] bytes=Base64.getUrlDecoder().decode(encoded);
            if(!Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(encoded))throw new IllegalArgumentException();
            var node=strict.readTree(bytes);
            if(node==null||!node.isObject()||node.size()!=2||!node.has("occurredAt")||!node.get("occurredAt").isTextual()
                    ||!node.has("id")||!node.get("id").isTextual())throw new IllegalArgumentException();
            String rawId=node.get("id").textValue();UUID id=UUID.fromString(rawId);
            if(rawId.length()!=36||!id.toString().equalsIgnoreCase(rawId))throw new IllegalArgumentException();
            Instant time=Instant.parse(node.get("occurredAt").textValue());
            if(time.getNano()%1000!=0||time.isBefore(Instant.parse("0001-01-01T00:00:00Z"))||time.isAfter(Instant.parse("9999-12-31T23:59:59.999999Z")))throw new IllegalArgumentException();
            return new AuditCursorPosition(time,id);
        } catch(java.io.IOException|IllegalArgumentException|java.time.DateTimeException exception) { throw new ApiException(ErrorCode.INVALID_CURSOR); }
    }

}
