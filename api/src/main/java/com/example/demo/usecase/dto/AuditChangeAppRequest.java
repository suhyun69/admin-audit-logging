package com.example.demo.usecase.dto;
public record AuditChangeAppRequest(String fieldName, String beforeValue, String afterValue) {}
