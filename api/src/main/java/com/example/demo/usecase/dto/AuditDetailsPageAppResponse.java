package com.example.demo.usecase.dto;
import java.util.List;
public record AuditDetailsPageAppResponse(List<AuditDetailAppResponse> items,String nextCursor,boolean hasNext) {}
