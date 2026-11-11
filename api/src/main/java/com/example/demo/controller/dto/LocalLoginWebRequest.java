package com.example.demo.controller.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LocalLoginWebRequest(@NotBlank @Size(max=100) String username) {
    @Override public String toString() {return "LocalLoginWebRequest[redacted]";}
}
