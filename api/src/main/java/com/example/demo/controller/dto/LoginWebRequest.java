package com.example.demo.controller.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LoginWebRequest(@NotBlank @Size(max=100) String username,@NotBlank String password) {
    @Override public String toString() { return "LoginWebRequest[redacted]"; }
}
