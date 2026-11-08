package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.UsersPageAppResponse;
import java.util.List;
public record UsersPageWebResponse(List<UserWebResponse> items,String nextCursor,boolean hasNext) {
    public static UsersPageWebResponse from(UsersPageAppResponse page) { return new UsersPageWebResponse(page.items().stream().map(UserWebResponse::from).toList(),page.nextCursor(),page.hasNext()); }
}
