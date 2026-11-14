package com.example.demo.controller.dto;

import com.example.demo.common.web.StrictLongDeserializer;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ChangeUserStatusWebRequest(
    @NotBlank String status,
    @NotNull @PositiveOrZero @JsonDeserialize(using = StrictLongDeserializer.class) Long version) {}
