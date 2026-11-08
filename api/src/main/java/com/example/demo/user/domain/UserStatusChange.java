package com.example.demo.user.domain;
public record UserStatusChange(long id,UserStatus previousStatus,UserStatus currentStatus,long version,boolean changed) {}
