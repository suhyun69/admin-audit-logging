package com.example.demo.user.application;

import com.example.demo.user.application.port.out.UserRepositoryPort;
import com.example.demo.user.domain.*;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional(propagation=Propagation.MANDATORY)
public class UserService {
    private final UserRepositoryPort users;
    public UserService(UserRepositoryPort users) { this.users=users; }
    public List<User> find(String search,UserStatus status,int limit,Long afterId) { return users.find(search,status,limit,afterId); }
    public User detail(long id) { return users.findById(id).orElseThrow(()->new ApiException(ErrorCode.USER_NOT_FOUND)); }
}
