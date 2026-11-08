package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditPageAppResponse;
import java.util.List;
public record AuditPageWebResponse(List<AuditItemWebResponse> items,String nextCursor,boolean hasNext) {
    public static AuditPageWebResponse from(AuditPageAppResponse page) { return new AuditPageWebResponse(page.items().stream().map(AuditItemWebResponse::from).toList(),page.nextCursor(),page.hasNext()); }
}
