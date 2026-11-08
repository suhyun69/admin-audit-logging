package com.example.demo.common.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RequestContextFilterTest {
    private final RequestContextFilter filter = new RequestContextFilter(new ObjectMapper().findAndRegisterModules());
    @AfterEach void clean() { RequestContext.clear(); MDC.clear(); }

    @Test
    void correlationIsValidatedAndDoesNotLeakToNextRequest() throws Exception {
        UUID interaction = UUID.randomUUID();
        var first = new MockHttpServletRequest("GET", "/api/v1/users");
        first.addHeader("X-Interaction-ID", interaction.toString());
        var firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, (request, response) -> {
            var context = RequestContext.current().orElseThrow();
            assertThat(context.interactionId()).isEqualTo(interaction);
            assertThat(MDC.get("requestId")).isEqualTo(context.requestId().toString());
            assertThat(context.durationMs()).isGreaterThanOrEqualTo(0);
        });
        assertThat(RequestContext.current()).isEmpty();
        assertThat(((RequestContext) first.getAttribute(RequestContext.ATTRIBUTE)).interactionId()).isEqualTo(interaction);
        assertThat(MDC.get("requestId")).isNull();
        var secondResponse = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/users"), secondResponse, (request, response) -> {
            assertThat(RequestContext.current().orElseThrow().interactionId()).isNull();
            assertThat(MDC.get("interactionId")).isNull();
        });
        assertThat(firstResponse.getHeader("X-Request-ID")).isNotEqualTo(secondResponse.getHeader("X-Request-ID"));
    }

    @Test
    void invalidShortenedAndBlankUuidsAreRejectedBeforeDownstream() throws Exception {
        for (String value : new String[]{"bad", "1-1-1-1-1", ""}) {
            var request = new MockHttpServletRequest("GET", "/api/v1/users");
            request.addHeader("X-Interaction-ID", value);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("invalid ID reached handler"));
            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(response.getContentAsString()).contains("VALIDATION_ERROR").doesNotContain(value.equals("") ? "raw-value" : "\""+value+"\"");
            assertThat(RequestContext.current()).isEmpty();
        }
    }

    @Test
    void unexpectedDownstreamFailureStillClearsContextAndReturnsSafeError() throws Exception {
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/users"), response,
                (req, res) -> { throw new IllegalStateException("secret"); });
        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("secret");
        assertThat(RequestContext.current()).isEmpty();
        assertThat(MDC.get("requestId")).isNull();
    }
}
