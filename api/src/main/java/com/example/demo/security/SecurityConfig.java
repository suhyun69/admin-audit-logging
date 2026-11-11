package com.example.demo.security;

import com.example.demo.common.exception.ErrorCode;
import com.example.demo.common.web.ApiErrors;
import com.example.demo.usecase.AuthUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean AuthenticationProvider sessionAuthenticationProvider(AuthUseCase auth) { return new OpaqueSessionAuthenticationProvider(auth); }
    @Bean CsrfTokenRepository csrfTokenRepository() { return new SessionBoundCsrfTokenRepository(); }
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AuthenticationProvider provider,CsrfTokenRepository csrf,ObjectMapper mapper,SecurityAuditRecorder audit,org.springframework.core.env.Environment environment) throws Exception {
        boolean local=environment.acceptsProfiles(org.springframework.core.env.Profiles.of("local"));
        return http
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable()).logout(config -> config.disable())
                .securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()))
                // DB-cookie authentication is rebuilt per request; only explicit login/logout rotates CSRF state.
                .sessionManagement(config -> config.requireExplicitAuthenticationStrategy(true))
                .requestCache(config -> config.requestCache(new NullRequestCache()))
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(config -> config
                        .requestMatchers(request -> request.getDispatcherType()==jakarta.servlet.DispatcherType.ASYNC && (request.getRequestURI().equals("/api/v1/audit-logs/stream") || local&&request.getRequestURI().equals("/api/v1/local/audit-logs/stream"))).permitAll()
                        .requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf","/actuator/health","/api/v1/local/accounts").permitAll()
                        .requestMatchers(request -> local && (request.getMethod().equals("POST")&&request.getRequestURI().equals("/api/v1/local/login")
                                ||request.getMethod().equals("GET")&&request.getRequestURI().equals("/api/v1/local/audit-logs/stream"))).permitAll()
                        .requestMatchers("/api/v1/local/**").denyAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/users/*/status").hasAnyRole("ADMIN","OPERATOR")
                        .requestMatchers(HttpMethod.GET,"/api/v1/audit-logs/me","/api/v1/audit-logs/summary","/api/v1/audit-logs/stream").authenticated()
                        .requestMatchers("/api/v1/audit-logs","/api/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/settings/admins","/api/v1/settings/admins/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request,response,exception) -> { audit.failed(ErrorCode.UNAUTHENTICATED); ApiErrors.write(response,mapper,ErrorCode.UNAUTHENTICATED); })
                        .accessDeniedHandler((request,response,exception) -> {
                            var code=exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED;
                            audit.failed(code); ApiErrors.write(response,mapper,code);
                        }))
                .addFilterBefore(new ClientPayloadLimitFilter(mapper),CsrfFilter.class)
                .addFilterBefore(new OpaqueSessionFilter(provider),ClientPayloadLimitFilter.class)
                .build();
    }
}
