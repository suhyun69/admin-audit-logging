package com.example.demo.usecase.dto;

import java.util.List;

public record UsersPageAppResponse(
    List<UserAppResponse> items, String nextCursor, boolean hasNext, long totalElements) {
  public UsersPageAppResponse(List<UserAppResponse> items, String nextCursor, boolean hasNext) {
    this(items, nextCursor, hasNext, items.size());
  }
}
