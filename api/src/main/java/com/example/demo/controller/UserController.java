package com.example.demo.controller;

import com.example.demo.controller.dto.ChangeUserStatusWebRequest;
import com.example.demo.controller.dto.StatusChangeWebResponse;
import com.example.demo.controller.dto.UserWebResponse;
import com.example.demo.controller.dto.UsersPageWebResponse;
import com.example.demo.usecase.ChangeUserStatusUseCase;
import com.example.demo.usecase.ReadUsersUseCase;
import com.example.demo.usecase.dto.ChangeUserStatusAppRequest;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
  private final ReadUsersUseCase reads;
  private final ChangeUserStatusUseCase changes;

  public UserController(ReadUsersUseCase reads, ChangeUserStatusUseCase changes) {
    this.reads = reads;
    this.changes = changes;
  }

  @GetMapping
  public UsersPageWebResponse list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status,
      @RequestParam(defaultValue = "20") Integer size,
      @RequestParam(required = false) String cursor) {
    return UsersPageWebResponse.from(reads.list(new UserSearchAppRequest(q, status, size, cursor)));
  }

  @PatchMapping("/{id}/status")
  public StatusChangeWebResponse change(
      @PathVariable long id, @Valid @RequestBody ChangeUserStatusWebRequest request) {
    return StatusChangeWebResponse.from(
        changes.change(id, new ChangeUserStatusAppRequest(request.status(), request.version())));
  }

  @GetMapping("/{id}")
  public UserWebResponse detail(@PathVariable long id) {
    return UserWebResponse.from(reads.detail(id));
  }
}
