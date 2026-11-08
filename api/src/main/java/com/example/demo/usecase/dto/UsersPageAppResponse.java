package com.example.demo.usecase.dto;
import java.util.List;
public record UsersPageAppResponse(List<UserAppResponse> items,String nextCursor,boolean hasNext) {}
