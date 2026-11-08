package com.example.demo.common.request;

import java.util.UUID;

/** Server-authenticated identity snapshot; never populated from request bodies or correlation headers. */
public record ActorSnapshot(long id, String name, String role, UUID sessionId) {}
