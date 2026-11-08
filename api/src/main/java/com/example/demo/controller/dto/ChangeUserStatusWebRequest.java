package com.example.demo.controller.dto;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.example.demo.common.web.StrictLongDeserializer;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
public record ChangeUserStatusWebRequest(@NotBlank String status,@NotNull @PositiveOrZero @JsonDeserialize(using=StrictLongDeserializer.class) Long version) {}
