package com.example.demo.controller;

import com.example.demo.controller.dto.ClientEventWebRequest;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

/** Exact root JSON schema; no ignored identity fields, duplicate keys, coercion, or trailing JSON. */
public class ClientEventDeserializer extends JsonDeserializer<ClientEventWebRequest> {
    private static final Set<String> ALLOWED=Set.of("eventId","interactionId","eventType","action","resourceType","resourceId","pagePath","occurredAt");
    private static final Set<String> OPTIONAL=Set.of("resourceType","resourceId");
    @Override public ClientEventWebRequest deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        if(!parser.hasToken(JsonToken.START_OBJECT))throw invalid(parser);
        var values=new HashMap<String,String>();
        while(parser.nextToken()!=JsonToken.END_OBJECT) {
            if(!parser.hasToken(JsonToken.FIELD_NAME))throw invalid(parser);
            String name=parser.currentName();
            if(!ALLOWED.contains(name)||values.containsKey(name))throw invalid(parser);
            var token=parser.nextToken();
            if(token==JsonToken.VALUE_STRING)values.put(name,parser.getText());
            else if(token==JsonToken.VALUE_NULL&&OPTIONAL.contains(name))values.put(name,null);
            else throw invalid(parser);
        }
        for(String name:ALLOWED)if(!OPTIONAL.contains(name)&&!values.containsKey(name))throw invalid(parser);
        if(parser.nextToken()!=null)throw invalid(parser);
        return new ClientEventWebRequest(values.get("eventId"),values.get("interactionId"),values.get("eventType"),values.get("action"),values.get("resourceType"),values.get("resourceId"),values.get("pagePath"),values.get("occurredAt"));
    }
    private JsonMappingException invalid(JsonParser parser) { return JsonMappingException.from(parser,"Invalid client event"); }
}
