package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditChangeAppResponse;
public record AuditChangeWebResponse(String fieldName,Object beforeValue,Object afterValue) {
    public static AuditChangeWebResponse from(AuditChangeAppResponse change) { return new AuditChangeWebResponse(change.fieldName(),change.beforeValue(),change.afterValue()); }
}
