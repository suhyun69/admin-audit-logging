package com.example.demo.usecase.dto;
public record UserSearchAppRequest(String q,String status,Integer size,String cursor) {
    @Override public String toString() { return "UserSearchAppRequest[search redacted]"; }
}
