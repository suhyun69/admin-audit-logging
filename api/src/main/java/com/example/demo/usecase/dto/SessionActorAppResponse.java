package com.example.demo.usecase.dto;
import java.util.UUID;
public record SessionActorAppResponse(ActorAppResponse actor, UUID sessionId) {}
