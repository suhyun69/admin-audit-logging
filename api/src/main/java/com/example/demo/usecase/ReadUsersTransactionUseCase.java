package com.example.demo.usecase;
import com.example.demo.usecase.dto.*;
public interface ReadUsersTransactionUseCase {
    UsersPageAppResponse list(UserSearchAppRequest request);
    UserAppResponse detail(long id);
}
