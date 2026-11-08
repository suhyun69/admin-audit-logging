package com.example.demo.controller.dto;
import com.example.demo.usecase.dto.ActorAppResponse;
public record ActorWebResponse(long id,String username,String displayName,String role) {
    public static ActorWebResponse from(ActorAppResponse actor) { return new ActorWebResponse(actor.id(),actor.username(),actor.displayName(),actor.role()); }
}
