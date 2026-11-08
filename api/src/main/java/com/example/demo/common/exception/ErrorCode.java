package com.example.demo.common.exception;

/** Framework-neutral codes and safe public messages. HTTP mapping belongs to the web boundary. */
public enum ErrorCode {
    VALIDATION_ERROR("요청 값이 올바르지 않습니다."),
    INVALID_CURSOR("페이지 커서가 올바르지 않습니다."),
    UNAUTHENTICATED("로그인이 필요합니다."),
    INVALID_CREDENTIALS("아이디 또는 비밀번호가 올바르지 않습니다."),
    ACCESS_DENIED("이 작업을 수행할 권한이 없습니다."),
    CSRF_INVALID("보안 토큰이 올바르지 않습니다."),
    USER_NOT_FOUND("회원을 찾을 수 없습니다."),
    AUDIT_NOT_FOUND("감사 기록을 찾을 수 없습니다."),
    RESOURCE_NOT_FOUND("리소스를 찾을 수 없습니다."),
    VERSION_CONFLICT("데이터가 변경되었습니다. 새로 조회해 주세요."),
    EVENT_CONFLICT("이벤트 식별자가 충돌했습니다."),
    PAYLOAD_TOO_LARGE("요청 크기 제한을 초과했습니다."),
    METHOD_NOT_ALLOWED("허용되지 않은 요청 메서드입니다."),
    UNSUPPORTED_MEDIA_TYPE("지원하지 않는 요청 형식입니다."),
    RATE_LIMITED("요청 횟수 제한을 초과했습니다."),
    INTERNAL_ERROR("서버 내부 오류가 발생했습니다.");

    private final String message;
    ErrorCode(String message) { this.message = message; }
    public String getMessage() { return message; }
}
