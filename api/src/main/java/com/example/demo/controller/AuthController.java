package com.example.demo.controller;

import com.example.demo.controller.dto.*;
import com.example.demo.usecase.AuthUseCase;
import com.example.demo.usecase.dto.LoginAppRequest;
import com.example.demo.usecase.dto.SessionActorAppResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import java.time.Duration;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthUseCase auth;
    private final CsrfTokenRepository csrf;
    private final boolean secure;
    public AuthController(AuthUseCase auth,CsrfTokenRepository csrf,@Value("${audit.cookie-secure:true}") boolean secure) {
        this.auth=auth; this.csrf=csrf; this.secure=secure;
    }
    @GetMapping("/csrf")
    public CsrfWebResponse csrf(HttpServletRequest request) {
        var token=(CsrfToken)request.getAttribute(CsrfToken.class.getName());
        return new CsrfWebResponse(token.getToken(),token.getHeaderName());
    }
    @PostMapping("/login")
    public ResponseEntity<ActorWebResponse> login(@Valid @RequestBody LoginWebRequest body,HttpServletRequest request,HttpServletResponse response) {
        var result=auth.login(new LoginAppRequest(body.username(),body.password()));
        if (request.getSession(false)!=null) request.changeSessionId();
        csrf.saveToken(null,request,response);
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie("AUDIT_SESSION",result.token(),Duration.ofHours(8)).toString())
                .body(ActorWebResponse.from(result.actor()));
    }
    @GetMapping("/me")
    public ActorWebResponse me(@AuthenticationPrincipal SessionActorAppResponse principal) { return ActorWebResponse.from(principal.actor()); }
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal SessionActorAppResponse principal,HttpServletRequest request,HttpServletResponse response) {
        auth.logout(principal.sessionId());
        csrf.saveToken(null,request,response);
        if (request.getSession(false)!=null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE,cookie("AUDIT_SESSION","",Duration.ZERO).toString(),cookie("AUDIT_CSRF_SESSION","",Duration.ZERO).toString()).build();
    }
    private ResponseCookie cookie(String name,String value,Duration age) {
        return ResponseCookie.from(name,value).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(age).build();
    }
}
