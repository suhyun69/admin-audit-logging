package com.example.demo.usecase.dto;
public record StatusChangeAppResponse(long id,String previousStatus,String currentStatus,long version) {}
