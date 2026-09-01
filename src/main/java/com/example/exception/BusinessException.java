package com.example.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반. {@link GlobalExceptionHandler} 가 잡아
 * {@link ErrorCode} 의 상태 코드와 메시지로 응답을 만든다.
 *
 * <p>메시지를 개별로 받지 않고 {@link ErrorCode} 만 받는다.
 * 호출부마다 문구를 다르게 쓰면 같은 상황에 다른 메시지가 나가고,
 * 특히 로그인 실패에서는 그 차이가 계정 존재 여부 노출로 이어진다(PRD 5.1).
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        // 로그에 남길 용도. 이 문자열이 클라이언트로 나가지는 않는다.
        super(errorCode.name());
        this.errorCode = errorCode;
    }
}
