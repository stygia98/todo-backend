package com.example.exception;

import com.example.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 컨트롤러 단계에서 발생한 예외를 {@link ApiResponse} 포맷으로 변환한다.
 *
 * <p>⚠️ <b>이 클래스는 컨트롤러에 진입한 요청의 예외만 잡는다.</b>
 * {@code @RestControllerAdvice} 는 DispatcherServlet 이후에 동작하므로,
 * JWT 가 없거나 만료돼 {@code JwtAuthenticationFilter} 단계에서 거부된 요청은 여기까지 오지 못한다.
 * 그쪽은 {@code JwtAuthenticationEntryPoint} 와 {@code JwtAccessDeniedHandler} 가 담당한다.
 * 둘을 함께 두지 않으면 "모든 응답이 {@code {success, data, error}} 포맷"이라는 규칙이 401 에서 깨진다.
 *
 * <p>어떤 핸들러도 <b>예외 메시지나 스택트레이스를 응답에 넣지 않는다.</b>
 * 내부 구조가 노출되기 때문이며, 원인 추적에 필요한 정보는 전부 로그로 남긴다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 비즈니스 규칙 위반. 상태 코드와 메시지는 {@link ErrorCode} 가 정한다. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("비즈니스 예외 발생: {}", errorCode.name());
        return ResponseEntity.status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * {@code @Valid} 검증 실패. 400 INVALID_INPUT 이며 필드별 사유를 메시지에 담는다.
     *
     * <p>비밀번호가 72바이트를 넘는 경우도 여기로 들어온다({@code @MaxByteLength}).
     * 인코딩 단계까지 가지 않고 검증에서 걸리는 것이 정상 경로다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + resolveMessage(fieldError))
                .collect(Collectors.joining(", "));

        log.warn("입력값 검증 실패: {}", detail);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT, detail));
    }

    /**
     * 요청 본문을 읽지 못함 → 400.
     *
     * <p>JSON 문법이 깨졌거나, 타입이 맞지 않거나(숫자 자리에 문자열), 본문이 비었을 때 발생한다.
     * <b>전부 클라이언트 잘못이므로 500이 아니라 400이다.</b>
     *
     * <p>이 핸들러가 없으면 아래 catch-all 이 잡아 500 {@code INTERNAL_ERROR} 로 나간다.
     * 클라이언트 오류가 서버 오류로 보고되면 원인 추적이 엉뚱한 곳을 향한다.
     *
     * <p>파싱 실패 위치 같은 상세는 응답에 넣지 않는다. 내부 구조가 드러난다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadableException(HttpMessageNotReadableException e) {
        log.warn("요청 본문을 읽지 못했습니다: {}", e.getMessage());
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT));
    }

    /**
     * {@link IllegalArgumentException} → 400.
     *
     * <p>CLAUDE.md 4장이 요구한 <b>안전장치</b>다. {@code @MaxByteLength} 를 우회한 경로로
     * 72바이트 초과 비밀번호가 {@code BCryptPasswordEncoder} 에 닿으면 이 예외가 나는데,
     * 매핑이 없으면 500 이 나간다.
     *
     * <p>⚠️ 다만 이 예외는 <b>프로그래밍 오류로도 발생한다.</b> 전부 400 으로 바꾸면
     * 서버 버그가 클라이언트 입력 오류로 위장되어 로그에서 사라진다.
     * 그래서 스택트레이스를 반드시 남긴다. 응답에는 고정 메시지만 나간다.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgumentException(IllegalArgumentException e) {
        log.warn("잘못된 인자로 요청이 거부되었습니다. 서버 측 결함일 수 있으니 확인이 필요합니다.", e);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.error(ErrorCode.INVALID_INPUT));
    }

    /**
     * 존재하지 않는 API 경로 → 404.
     *
     * <p>2026-09-01 실측으로 발견했다. 이 핸들러가 없으면 {@code NoResourceFoundException} 이
     * 아래 catch-all 에 걸려 500 {@code INTERNAL_ERROR} 로 나간다. {@code TODO_NOT_FOUND} 를
     * 쓰지 않는다 — 메시지가 "할 일을 찾을 수 없습니다"라 API 경로 오류에는 어긋난다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException e) {
        log.warn("존재하지 않는 경로 요청: {}", e.getResourcePath());
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(ApiResponse.error(ErrorCode.NOT_FOUND));
    }

    /**
     * 지원하지 않는 HTTP 메서드 → 405.
     *
     * <p>위 핸들러와 원인이 같다. 존재하는 경로라도 등록되지 않은 메서드(예: 존재하는
     * {@code /api/v1/auth/me} 에 {@code DELETE})로 호출하면 catch-all 에 걸려 500 으로 나가던 것을
     * 2026-09-01 실측으로 발견했다.
     *
     * <p>⚠️ <b>토큰 없이 재현하면 나타나지 않는다.</b> Security 필터가 먼저 401 로 막아
     * 컨트롤러까지 요청이 도달하지 못하기 때문이다. 인증된 상태에서만 재현된다.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException e) {
        log.warn("지원하지 않는 메서드 요청: {}", e.getMethod());
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.error(ErrorCode.METHOD_NOT_ALLOWED));
    }

    /**
     * 처리하지 못한 모든 예외. 원인은 로그에만 남기고 응답에는 고정 메시지를 쓴다.
     *
     * <p>⚠️ <b>이 핸들러는 Spring MVC 표준 예외까지 삼킨다.</b> 위 두 핸들러가 대표 사례를 가로챈다.
     * 새로운 표준 예외가 500 으로 나가는 것을 발견하면, 여기서 원인을 찾기보다
     * 위쪽에 구체적인 핸들러를 추가하는 편이 맞다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("처리되지 않은 예외가 발생했습니다.", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR));
    }

    /** 검증 애노테이션에 message 가 없으면 기본 문구로 대체한다. */
    private String resolveMessage(FieldError fieldError) {
        String message = fieldError.getDefaultMessage();
        return (message != null && !message.isBlank())
                ? message
                : ErrorCode.INVALID_INPUT.getMessage();
    }
}
