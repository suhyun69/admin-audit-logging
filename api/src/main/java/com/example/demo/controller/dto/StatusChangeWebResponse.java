package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.StatusChangeAppResponse;
public record StatusChangeWebResponse(long id,String previousStatus,String currentStatus,long version) {
    public static StatusChangeWebResponse from(StatusChangeAppResponse response) { return new StatusChangeWebResponse(response.id(),response.previousStatus(),response.currentStatus(),response.version()); }
}
