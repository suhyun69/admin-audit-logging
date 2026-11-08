package com.example.demo.common.web;

import com.example.demo.common.exception.NotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Common error behavior remains tested without depending on an example domain. */
class GlobalExceptionHandlerTest {
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ErrorProbeController())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void notFoundIsTranslated() throws Exception {
        mockMvc.perform(get("/test/errors/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mockMvc.perform(post("/test/errors").contentType(MediaType.APPLICATION_JSON).content("{\"value\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("요청 본문을 읽을 수 없습니다."));
    }

    @Test
    void typeMismatchIsBadRequest() throws Exception {
        mockMvc.perform(get("/test/errors/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("'id' 값이 올바르지 않습니다."));
    }

    @Test
    void validationFailureCarriesFieldErrors() throws Exception {
        mockMvc.perform(post("/test/errors").contentType(MediaType.APPLICATION_JSON).content("{\"value\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.value").exists());
    }

    @Test
    void errorResponseOmitsNullFields() throws Exception {
        mockMvc.perform(get("/test/errors/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }

    @RestController
    static class ErrorProbeController {
        @GetMapping("/test/errors/{id}")
        String get(@PathVariable long id) {
            throw new NotFoundException("리소스를 찾을 수 없습니다.");
        }

        @PostMapping("/test/errors")
        String post(@Valid @RequestBody ErrorProbeRequest body) {
            return body.value();
        }
    }

    record ErrorProbeRequest(@NotBlank String value) {}
}
