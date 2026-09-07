package com.example.exception;

import com.example.dto.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 12에서 추가한 {@link ErrorCode} 4종이 {@link GlobalExceptionHandler}를 거쳐
 * 올바른 HTTP 상태·한글 메시지로 응답되는지 확인한다.
 *
 * <p>이 4종을 실제로 던지는 컨트롤러(AttachmentController)는 이후 태스크의 산출물이라
 * 아직 없다. {@code GlobalExceptionHandler.handleBusinessException}은 {@link ErrorCode}
 * 종류를 가리지 않는 범용 매핑이므로, 컨트롤러 없이도 이 메서드를 직접 호출해
 * 매핑 자체를 검증할 수 있다.
 */
class ErrorCodeTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @ParameterizedTest(name = "{0} 은 지정된 상태 코드와 메시지로 응답된다")
    @EnumSource(value = ErrorCode.class, names = {
            "ATTACHMENT_NOT_FOUND", "ATTACHMENT_ALREADY_UPLOADED", "FILE_TOO_LARGE", "UNSUPPORTED_FILE_TYPE"
    })
    @DisplayName("첨부 관련 ErrorCode 4종이 BusinessException으로 던져지면 올바르게 응답된다")
    void attachmentErrorCodesMapCorrectly(ErrorCode errorCode) {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleBusinessException(new BusinessException(errorCode));

        assertThat(response.getStatusCode()).isEqualTo(errorCode.getStatus());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().data()).isNull();
        assertThat(response.getBody().error().code()).isEqualTo(errorCode.name());
        assertThat(response.getBody().error().message()).isEqualTo(errorCode.getMessage());
    }

    @ParameterizedTest(name = "{0} 은 {1} 상태다")
    @org.junit.jupiter.params.provider.CsvSource({
            "ATTACHMENT_NOT_FOUND, NOT_FOUND",
            "ATTACHMENT_ALREADY_UPLOADED, CONFLICT",
            "FILE_TOO_LARGE, BAD_REQUEST",
            "UNSUPPORTED_FILE_TYPE, BAD_REQUEST"
    })
    @DisplayName("각 코드의 HTTP 상태가 문서(CLAUDE.md 6장/PRD 5.1)와 일치한다")
    void statusMatchesDocumentedValue(ErrorCode errorCode, HttpStatus expected) {
        assertThat(errorCode.getStatus()).isEqualTo(expected);
    }
}
