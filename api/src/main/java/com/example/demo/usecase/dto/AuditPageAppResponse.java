package com.example.demo.usecase.dto;
import java.util.List;
public record AuditPageAppResponse(List<AuditItemAppResponse> items,String nextCursor,boolean hasNext) {}
