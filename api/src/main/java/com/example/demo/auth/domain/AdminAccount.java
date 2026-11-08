package com.example.demo.auth.domain;
public record AdminAccount(long id, String username, String passwordHash, String displayName, AdminRole role, boolean enabled) {
    @Override public String toString() { return "AdminAccount[id=" + id + ", role=" + role + "]"; }
}
