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
    public UserStatusChange changeStatus(long id,long expectedVersion,UserStatus target) {
        var before=detail(id);
        if(before.version()!=expectedVersion)throw new ApiException(ErrorCode.VERSION_CONFLICT);
        if(before.status()==target)return new UserStatusChange(id,before.status(),target,before.version(),false);
        if(!users.changeStatus(id,expectedVersion,target))throw new ApiException(ErrorCode.VERSION_CONFLICT);
        return new UserStatusChange(id,before.status(),target,expectedVersion+1,true);
    }
    public User detail(long id) { return users.findById(id).orElseThrow(()->new ApiException(ErrorCode.USER_NOT_FOUND)); }
}
