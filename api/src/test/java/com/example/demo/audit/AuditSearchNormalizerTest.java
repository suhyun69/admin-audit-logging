package com.example.demo.audit;

import com.example.demo.common.page.CursorCodec;
import com.example.demo.usecase.dto.AuditSearchAppRequest;
import com.example.demo.usecase.support.AuditSearchNormalizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

class AuditSearchNormalizerTest {
    private final Instant now=Instant.parse("2026-11-08T12:00:00.123456Z");
    private final AuditSearchNormalizer normalizer=new AuditSearchNormalizer(new CursorCodec(new ObjectMapper()),Clock.fixed(now,ZoneOffset.UTC));
    private AuditSearchAppRequest request(String from,String to) { return new AuditSearchAppRequest(null,null,null,null,null,null,null,from,to,null,null); }
    @Test void supportsBothMissingAndEachPartialTimeRange() {
        var defaults=normalizer.normalize(request(null,null),null,"AUDIT_SEARCH");assertThat(defaults.to()).isEqualTo(now);assertThat(defaults.from()).isEqualTo(now.minus(Duration.ofHours(24)));
        var toOnly=normalizer.normalize(request(null,"2026-11-07T00:00:00Z"),null,"AUDIT_SEARCH");assertThat(toOnly.from()).isEqualTo(Instant.parse("2026-11-06T00:00:00Z"));
        var fromOnly=normalizer.normalize(request("2026-11-08T00:00:00Z",null),null,"AUDIT_SEARCH");assertThat(fromOnly.to()).isEqualTo(now);
    }
    @Test void permitsExactlyThirtyOneDaysAndRejectsLongerOrUnrepresentableDates() {
        assertThat(normalizer.normalize(request(now.minus(Duration.ofDays(31)).toString(),now.toString()),null,"AUDIT_SEARCH").size()).isEqualTo(20);
        assertThatThrownBy(()->normalizer.normalize(request(now.minus(Duration.ofDays(31)).minusNanos(1).toString(),now.toString()),null,"AUDIT_SEARCH")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->normalizer.normalize(request("+100000-01-01T00:00:00Z","+100000-01-02T00:00:00Z"),null,"AUDIT_SEARCH")).isInstanceOf(RuntimeException.class);
    }
}
