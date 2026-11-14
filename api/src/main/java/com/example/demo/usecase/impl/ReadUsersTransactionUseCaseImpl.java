package com.example.demo.usecase.impl;

import com.example.demo.common.page.CursorCodec;
import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.User;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.ReadUsersTransactionUseCase;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import com.example.demo.usecase.support.audit.*;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReadUsersTransactionUseCaseImpl implements ReadUsersTransactionUseCase {
    private final UserService users;
    private final CursorCodec cursors;
    public ReadUsersTransactionUseCaseImpl(UserService users,CursorCodec cursors) { this.users=users;this.cursors=cursors; }
    @Override @Audited(Operation.USER_LIST) public UsersPageAppResponse list(UserSearchAppRequest request) {
        var found=users.find(request.q(),request.status()==null?null:UserStatus.valueOf(request.status()),request.size()+1,cursors.user(request.cursor()));
        boolean hasNext=found.size()>request.size();
        var page=found.stream().limit(request.size()).map(this::response).toList();
        return new UsersPageAppResponse(page,hasNext?cursors.user(page.get(page.size()-1).id()):null,hasNext);
    }
    @Override @Audited(Operation.USER_DETAIL) public UserAppResponse detail(long id) {
        var user=users.detail(id);
        return response(user);
    }
    private UserAppResponse response(User user) { return new UserAppResponse(user.id(),user.email(),user.name(),user.status().name(),user.version(),user.createdAt(),user.updatedAt()); }
}
