package com.example.demo.auth.domain;
public record IssuedSession(AdminAccount account, AdminSession session, String token) {
    @Override public String toString() { return "IssuedSession[session=" + session.id() + "]"; }
}
