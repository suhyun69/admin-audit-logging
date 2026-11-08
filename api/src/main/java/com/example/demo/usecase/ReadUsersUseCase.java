package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
public interface ReadUsersUseCase {
    UsersPageAppResponse list(UserSearchAppRequest request);
    UserAppResponse detail(long id);
}
