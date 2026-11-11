package com.example.demo.controller;

import com.example.demo.controller.dto.*;
import com.example.demo.common.exception.ApiException;
import com.example.demo.common.exception.ErrorCode;
import com.example.demo.usecase.LocalAuthUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.*;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import java.time.Duration;
import java.util.Map;

@RestController
@Profile("local")
@RequestMapping("/api/v1/local")
public class LocalAuthController {
    private final LocalAuthUseCase local;
    private final CsrfTokenRepository csrf;
    private final boolean secure;
    public LocalAuthController(LocalAuthUseCase local,CsrfTokenRepository csrf,@Value("${audit.cookie-secure:true}") boolean secure) {
        this.local=local;this.csrf=csrf;this.secure=secure;
    }
    @GetMapping("/accounts")
    public LocalAccountsWebResponse accounts(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        return new LocalAccountsWebResponse(local.accounts().stream().map(ActorWebResponse::from).toList());
    }
    @PostMapping("/login")
    public ResponseEntity<ActorWebResponse> login(@Valid @RequestBody LocalLoginWebRequest body,HttpServletRequest request,HttpServletResponse response) {
        var result=local.login(body.username());
        if(request.getSession(false)!=null)request.changeSessionId();
        csrf.saveToken(null,request,response);
        var cookie=ResponseCookie.from("AUDIT_SESSION",result.token()).httpOnly(true).secure(secure).sameSite("Lax").path("/").maxAge(Duration.ofHours(8)).build();
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,cookie.toString()).body(ActorWebResponse.from(result.actor()));
    }
    @GetMapping(value="/audit-logs/stream",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestHeader(name="Last-Event-ID",required=false) String cursor,@RequestParam Map<String,String> params,HttpServletResponse response) {
        if(!params.isEmpty())throw new ApiException(ErrorCode.VALIDATION_ERROR);
        response.setHeader("Cache-Control","no-store");response.setHeader("X-Accel-Buffering","no");
        return local.stream(cursor);
    }
}
