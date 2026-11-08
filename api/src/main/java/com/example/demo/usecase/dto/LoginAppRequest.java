package com.example.demo.usecase.dto;
public record LoginAppRequest(String username, String password) {
    @Override public String toString() { return "LoginAppRequest[redacted]"; }
}
