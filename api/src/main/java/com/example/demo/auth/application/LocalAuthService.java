package com.example.demo.auth.application;

import com.example.demo.auth.application.port.out.AdminSessionPort;
import com.example.demo.auth.domain.AdminAccount;
import com.example.demo.auth.domain.IssuedSession;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Profile("local")
@Transactional(propagation=Propagation.MANDATORY)
public class LocalAuthService {
    private final AdminSessionPort accounts;
    private final AuthService sessions;
    public LocalAuthService(AdminSessionPort accounts,AuthService sessions) {this.accounts=accounts;this.sessions=sessions;}
    public List<AdminAccount> accounts() {return accounts.enabledAccounts();}
    public IssuedSession login(String username) {
        if(username==null||username.isBlank()||username.length()>100)throw new ApiException(ErrorCode.VALIDATION_ERROR);
        var account=accounts.findAccount(username).filter(AdminAccount::enabled)
                .orElseThrow(()->new ApiException(ErrorCode.INVALID_CREDENTIALS));
        return sessions.issueSession(account);
    }
}
