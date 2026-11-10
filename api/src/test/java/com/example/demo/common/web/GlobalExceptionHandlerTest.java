package com.example.demo.common.web;

import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.request.RequestContext;
import com.example.demo.common.request.RequestContextFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .addFilters(new RequestContextFilter(mapper)).build();
    }
    @Test
    void allErrorsUseSafeContractAndServerOwnedRequestId() throws Exception {
        var response = mockMvc.perform(get("/test/errors/1").header("X-Request-ID", "forged"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value(ErrorCode.USER_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(jsonPath("$.timestamp").doesNotExist())
                .andReturn().getResponse();
        var id = response.getHeader("X-Request-ID");
        assertThat(UUID.fromString(id)).isNotNull();
        assertThat(mapper.readTree(response.getContentAsString()).get("requestId").asText()).isEqualTo(id);
        assertThat(RequestContext.current()).isEmpty();
    }
    @Test
    void malformedJsonAndTypeMismatchAreValidationErrors() throws Exception {
        mockMvc.perform(post("/test/errors").contentType(MediaType.APPLICATION_JSON).content("{\"value\":"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(get("/test/errors/not-a-number"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
    @Test
    void validationDoesNotEchoSensitiveValuesOrFieldMessages() throws Exception {
        mockMvc.perform(post("/test/errors").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(ErrorCode.VALIDATION_ERROR.getMessage()))
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
    @Test
    void internalAndIllegalArgumentMessagesNeverLeak() throws Exception {
        mockMvc.perform(get("/test/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }
    @Test
    void unsupportedMethodAndMediaTypeKeepTheirHttpMeaning() throws Exception {
        mockMvc.perform(delete("/test/errors"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", org.hamcrest.Matchers.containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").exists());
        mockMvc.perform(post("/test/errors").contentType(MediaType.TEXT_PLAIN).content("secret"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    @Test
    void disconnectedStreamKeepsCommittedSseBodyWithoutJsonError() throws Exception {
        mockMvc.perform(get("/test/disconnected"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string("event:ready\ndata:{}\n\n"));
    }

    @RestController
    static class ErrorProbeController {
        @GetMapping("/test/disconnected")
        void disconnected(jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
            response.setContentType("text/event-stream");
            response.getWriter().write("event:ready\ndata:{}\n\n");
            response.flushBuffer();
            throw new org.springframework.web.context.request.async.AsyncRequestNotUsableException("peer disconnected");
        }
        @GetMapping("/test/errors/{id}")
        String get(@PathVariable long id) { throw new ApiException(ErrorCode.USER_NOT_FOUND); }
        @PostMapping("/test/errors")
        String post(@Valid @RequestBody ErrorProbeRequest body) { return body.value(); }
        @GetMapping("/test/internal")
        String internal() { throw new IllegalStateException("secret sql/body/password"); }
        @GetMapping("/test/invalid")
        String invalid() { throw new IllegalArgumentException("secret search value"); }
    }
    record ErrorProbeRequest(@NotBlank String value) {}
}
