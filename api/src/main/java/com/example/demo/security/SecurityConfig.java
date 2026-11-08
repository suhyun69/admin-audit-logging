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
import org.springframework.security.config.http.SessionCreationPolicy;
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
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,AuthenticationProvider provider,CsrfTokenRepository csrf,ObjectMapper mapper) throws Exception {
        return http
                .formLogin(config -> config.disable()).httpBasic(config -> config.disable()).logout(config -> config.disable())
                .securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()))
                .sessionManagement(config -> config.sessionCreationPolicy(SessionCreationPolicy.NEVER))
                .requestCache(config -> config.requestCache(new NullRequestCache()))
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .authorizeHttpRequests(config -> config
                        .requestMatchers(HttpMethod.GET,"/api/v1/auth/csrf","/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST,"/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.PATCH,"/api/v1/users/*/status").hasAnyRole("ADMIN","OPERATOR")
                        .requestMatchers(HttpMethod.GET,"/api/v1/audit-logs/me","/api/v1/audit-logs/summary").authenticated()
                        .requestMatchers("/api/v1/audit-logs","/api/v1/audit-logs/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/settings/admins","/api/v1/settings/admins/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request,response,exception) -> ApiErrors.write(response,mapper,ErrorCode.UNAUTHENTICATED))
                        .accessDeniedHandler((request,response,exception) -> ApiErrors.write(response,mapper,
                                exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED)))
                .addFilterBefore(new OpaqueSessionFilter(provider),CsrfFilter.class)
                .build();
    }
}
