package com.example.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 에러 코드. CLAUDE.md 11장 표와 1:1로 대응한다.
 *
 * <p>클라이언트에 나가는 {@code code} 는 {@link #name()} 을 그대로 쓴다.
 * 별도 문자열 필드를 두면 enum 상수명과 어긋날 수 있고, 그 불일치는 컴파일러가 잡아주지 못한다.
 *
 * <p>{@code message} 는 <b>사용자에게 그대로 보여줄 한글 문구</b>다.
 * 예외의 원본 메시지나 스택트레이스를 여기에 섞지 않는다. 내부 구조가 노출된다.
 *
 * <p>{@code TODO_NOT_FOUND} 는 Phase 4 에서 쓰지만 지금 넣어둔다.
 * CLAUDE.md 11장이 6개를 규정하고 있어, 나중에 enum 을 다시 여는 것보다 한 번에 맞추는 편이 낫다.
 *
 * <p>{@code NOT_FOUND} 와 {@code METHOD_NOT_ALLOWED} 는 Phase 4 착수 전 실측으로 추가했다.
 * {@code @ExceptionHandler(Exception.class)} catch-all 이 Spring MVC 표준 예외
 * ({@code NoResourceFoundException}, {@code HttpRequestMethodNotSupportedException}) 를
 * 종류를 가리지 않고 삼켜 전부 500 {@code INTERNAL_ERROR} 로 나가고 있었다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    /** 유효성 검증 실패. 필드별 메시지는 GlobalExceptionHandler 가 조합한다. */
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "입력값이 올바르지 않습니다."),

    /**
     * 인증 실패 또는 토큰 만료.
     *
     * <p>⚠️ <b>로그인 실패는 원인과 무관하게 전부 이 코드 하나만 쓴다.</b>
     * 미가입 이메일과 비밀번호 오류를 구분해 응답하면 계정 존재 여부가 노출된다(PRD 5.1).
     * 메시지를 두 개 만든 뒤 같게 맞추는 방식은 한쪽만 수정되어 조용히 깨지므로 쓰지 않는다.
     */
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다."),

    /** 인증은 되었으나 권한이 없음. */
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    /**
     * 리소스 없음 또는 소유자 불일치.
     *
     * <p>타인의 Todo 에 접근하면 403 이 아니라 404 를 준다. 403 은 "존재하지만 권한이 없다"는
     * 사실을 알려주므로 리소스 존재 여부가 새어나간다(CLAUDE.md 6장).
     */
    TODO_NOT_FOUND(HttpStatus.NOT_FOUND, "할 일을 찾을 수 없습니다."),

    /**
     * 존재하지 않는 API 경로.
     *
     * <p>{@code TODO_NOT_FOUND} 와 겸용하지 않는다. 메시지가 "할 일을 찾을 수 없습니다"라
     * 경로 오류에 쓰면 문구가 어긋난다.
     */
    NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 리소스를 찾을 수 없습니다."),

    /** 해당 경로가 지원하지 않는 HTTP 메서드로 호출됨. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 요청 방식입니다."),

    /** 회원가입 시 이메일 중복. */
    EMAIL_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),

    /** 처리하지 못한 서버 오류. 원인은 로그에만 남긴다. */
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;
}
