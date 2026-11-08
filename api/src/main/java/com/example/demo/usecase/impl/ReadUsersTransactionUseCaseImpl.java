package com.example.demo.usecase.impl;

import com.example.demo.common.page.CursorCodec;
import com.example.demo.user.application.UserService;
import com.example.demo.user.domain.User;
import com.example.demo.user.domain.UserStatus;
import com.example.demo.usecase.AuditWriteUseCase;
import com.example.demo.usecase.ReadUsersTransactionUseCase;
import com.example.demo.usecase.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional
public class ReadUsersTransactionUseCaseImpl implements ReadUsersTransactionUseCase {
    private final UserService users;
    private final AuditWriteUseCase audit;
    private final CursorCodec cursors;
    public ReadUsersTransactionUseCaseImpl(UserService users,AuditWriteUseCase audit,CursorCodec cursors) { this.users=users;this.audit=audit;this.cursors=cursors; }
    @Override public UsersPageAppResponse list(UserSearchAppRequest request) {
        var found=users.find(request.q(),request.status()==null?null:UserStatus.valueOf(request.status()),request.size()+1,cursors.user(request.cursor()));
        boolean hasNext=found.size()>request.size();
        var page=found.stream().limit(request.size()).map(this::response).toList();
        var metadata=new LinkedHashMap<String,Object>();metadata.put("searched",!request.q().isEmpty());metadata.put("size",request.size());metadata.put("returnedCount",page.size());
        if(request.status()!=null)metadata.put("status",request.status());
        audit.record(AuditWriteAppRequest.event("DATA_READ","USER_LIST","BUSINESS","SUCCESS").resource("USER",null,metadata,List.of()));
        return new UsersPageAppResponse(page,hasNext?cursors.user(page.get(page.size()-1).id()):null,hasNext);
    }
    @Override public UserAppResponse detail(long id) {
        var user=users.detail(id);
        audit.record(AuditWriteAppRequest.event("DATA_READ","USER_DETAIL","BUSINESS","SUCCESS").resource("USER",Long.toString(id),java.util.Map.of(),List.of()));
        return response(user);
    }
    private UserAppResponse response(User user) { return new UserAppResponse(user.id(),user.email(),user.name(),user.status().name(),user.version(),user.createdAt(),user.updatedAt()); }
}
