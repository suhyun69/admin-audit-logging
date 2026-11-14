package com.example.demo.usecase;

import com.example.demo.usecase.dto.UserAppResponse;
import com.example.demo.usecase.dto.UserSearchAppRequest;
import com.example.demo.usecase.dto.UsersPageAppResponse;

public interface ReadUsersUseCase {
  UsersPageAppResponse list(UserSearchAppRequest request);

  UserAppResponse detail(long id);
}
