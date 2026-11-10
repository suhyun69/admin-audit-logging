package com.example.demo.audit;

import com.example.demo.audit.domain.*;
import com.example.demo.controller.dto.AuditItemWebResponse;
import com.example.demo.usecase.support.AuditResponseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuditResponseMapperTest {
    private final AuditResponseMapper mapper=new AuditResponseMapper();
    private AuditEvent event(AuditEventType type,Map<String,Object> metadata) {
        return new AuditEvent(UUID.randomUUID(),Instant.now(),Instant.now(),type,"PAGE_ENTER",1L,"Admin","ADMIN",null,
                null,null,AuditSource.CLIENT,null,null,null,null,null,null,AuditResult.ATTEMPTED,null,null,null,metadata);
    }
    @Test void pageViewsExposeOnlyAnAllowlistedPathWithoutQueryOrFragment() {
        for(String path:new String[]{"/users","/users/123","/dashboard"}) {
            var item=mapper.item(event(AuditEventType.PAGE_VIEW,Map.of("pagePath",path+"?q=secret#private","password","hidden")));
            assertThat(item.pagePath()).isEqualTo(path);
            var web=AuditItemWebResponse.from(item);
            assertThat(web.pagePath()).isEqualTo(path);
            var json=new ObjectMapper().findAndRegisterModules().valueToTree(web);
            assertThat(json.get("pagePath").asText()).isEqualTo(path);
            assertThat(json.has("metadata")).isFalse();
            assertThat(json.toString()).doesNotContain("secret","private","password","hidden");
        }
    }
    @Test void disallowedMissingOrNonPageViewPathsAreNotExposed() {
        for(Map<String,Object> metadata:java.util.List.of(Map.<String,Object>of(),Map.<String,Object>of("pagePath","/private/secret"),Map.<String,Object>of("pagePath",123))) {
            assertThat(mapper.item(event(AuditEventType.PAGE_VIEW,metadata)).pagePath()).isNull();
        }
        assertThat(mapper.item(event(AuditEventType.BUTTON_CLICK,Map.of("pagePath","/users/123"))).pagePath()).isNull();
    }
}
