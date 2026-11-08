package com.example.demo.common.page;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.*;

class CursorCodecTest {
    private final CursorCodec cursors=new CursorCodec(new ObjectMapper());
    private String encode(String json) { return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8)); }
    @Test void validCursorRoundTripsAndAbsentCursorIsFirstPage() {
        assertThat(cursors.user(cursors.user(123L))).isEqualTo(123L);assertThat(cursors.user((String)null)).isNull();
    }
    @Test void rejectsBadSchemaDuplicateFieldsTrailingDataAndNonPositiveOrOverflowIds() {
        for(String json:new String[]{"{}","[]","{\"id\":0}","{\"id\":-1}","{\"id\":1.5}","{\"id\":\"1\"}","{\"id\":9223372036854775808}","{\"id\":1,\"q\":\"private\"}","{\"id\":1,\"id\":2}","{\"id\":1} {}"})
            assertThatThrownBy(()->cursors.user(encode(json))).isInstanceOfSatisfying(ApiException.class,error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }
    @Test void rejectsInvalidOrPaddedBase64AndUnboundedInputs() {
        for(String value:new String[]{"","@@@",encode("{\"id\":1}")+"=","a".repeat(513)})
            assertThatThrownBy(()->cursors.user(value)).isInstanceOf(ApiException.class);
    }
    @Test void auditCursorPreservesNativeUuidAndAllSixMicrosecondDigits() {
        var instant=java.time.Instant.parse("2026-11-08T12:00:00.123456Z");var id=java.util.UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        assertThat(cursors.audit(cursors.audit(instant,id))).isEqualTo(new AuditCursorPosition(instant,id));
    }
    @Test void auditCursorRejectsUnknownDuplicateMalformedAndSubMicrosecondCoordinates() {
        String id="ffffffff-ffff-ffff-ffff-ffffffffffff";
        for(String json:new String[]{"{\"occurredAt\":\"2026-11-08T00:00:00Z\",\"id\":\""+id+"\",\"other\":1}",
                "{\"occurredAt\":\"2026-11-08T00:00:00.000000001Z\",\"id\":\""+id+"\"}",
                "{\"occurredAt\":\"bad\",\"id\":\""+id+"\"}",
                "{\"occurredAt\":\"2026-11-08T00:00:00Z\",\"id\":\"1-1-1-1-1\"}",
                "{\"occurredAt\":\"2026-11-08T00:00:00Z\",\"id\":\""+id+"\",\"id\":\""+id+"\"}"})
            assertThatThrownBy(()->cursors.audit(encode(json))).isInstanceOfSatisfying(ApiException.class,error->assertThat(error.getErrorCode()).isEqualTo(ErrorCode.INVALID_CURSOR));
    }

}
