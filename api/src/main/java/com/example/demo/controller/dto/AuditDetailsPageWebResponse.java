package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.AuditDetailsPageAppResponse;
import java.util.List;
public record AuditDetailsPageWebResponse(List<AuditDetailWebResponse> items,String nextCursor,boolean hasNext) {
    public static AuditDetailsPageWebResponse from(AuditDetailsPageAppResponse page) { return new AuditDetailsPageWebResponse(page.items().stream().map(AuditDetailWebResponse::from).toList(),page.nextCursor(),page.hasNext()); }
}
