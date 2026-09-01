package com.example.dto;

import com.example.exception.ErrorCode;

/**
 * 모든 REST 응답의 공통 봉투. CLAUDE.md 5장의 포맷을 그대로 구현한다.
 *
 * <pre>
 * 성공: { "success": true,  "data": { ... }, "error": null }
 * 실패: { "success": false, "data": null,    "error": { "code": "...", "message": "..." } }
 * </pre>
 *
 * <p><b>목록 API 도 이 포맷을 따른다.</b> Phase 4 의 {@code PageResponse} 는 최상위가 아니라
 * {@code data} 안에 들어간다. 프론트는 {@code res.data.data.content} 로 접근한다.
 *
 * <p>⚠️ <b>이 포맷이 적용되지 않는 곳이 둘 있다.</b>
 * 하나는 OAuth2 콜백으로, REST 응답이 아니라 302 리다이렉트다(Phase 5).
 * 다른 하나는 Security 필터 단계에서 거부된 401/403 인데, 컨트롤러에 진입하지 못해
 * {@code @RestControllerAdvice} 가 잡지 못한다. 그쪽은 {@code JwtAuthenticationEntryPoint} 와
 * {@code JwtAccessDeniedHandler} 가 이 레코드를 직접 직렬화해 응답에 쓴다.
 *
 * @param success 성공 여부
 * @param data    성공 시 본문. 실패면 null
 * @param error   실패 시 에러 정보. 성공이면 null
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    /**
     * 에러 상세.
     *
     * @param code    {@link ErrorCode} 의 상수명
     * @param message 사용자에게 보여줄 한글 문구
     */
    public record ErrorBody(String code, String message) {
    }

    /** 본문이 있는 성공 응답. */
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    /** 본문이 없는 성공 응답. 삭제처럼 돌려줄 값이 없을 때 쓴다. */
    public static <T> ApiResponse<T> ok() {
        return new ApiResponse<>(true, null, null);
    }

    /**
     * 실패 응답. 메시지는 {@link ErrorCode} 에 정의된 것만 쓴다.
     *
     * <p>호출부에서 문구를 따로 넘기지 못하게 막아둔 것이다.
     * 같은 상황에 다른 메시지가 나가는 일을 구조적으로 방지한다.
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, null,
                new ErrorBody(errorCode.name(), errorCode.getMessage()));
    }

    /**
     * 실패 응답 + 개별 메시지.
     *
     * <p>검증 실패에서 필드별 사유를 담을 때만 쓴다({@code INVALID_INPUT}).
     * {@code code} 는 여전히 {@link ErrorCode} 것을 쓰므로 프론트의 분기 로직은 영향받지 않는다.
     */
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null,
                new ErrorBody(errorCode.name(), message));
    }
}
