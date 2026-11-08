package com.example.demo.controller;

import com.example.demo.controller.dto.*;
import com.example.demo.usecase.ReadUsersUseCase;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final ReadUsersUseCase reads;
    public UserController(ReadUsersUseCase reads) { this.reads=reads; }
    @GetMapping
    public UsersPageWebResponse list(@RequestParam(required=false) String q,@RequestParam(required=false) String status,
            @RequestParam(defaultValue="20") Integer size,@RequestParam(required=false) String cursor) {
        return UsersPageWebResponse.from(reads.list(new UserSearchAppRequest(q,status,size,cursor)));
    }
    @GetMapping("/{id}")
    public UserWebResponse detail(@PathVariable long id) { return UserWebResponse.from(reads.detail(id)); }
}
