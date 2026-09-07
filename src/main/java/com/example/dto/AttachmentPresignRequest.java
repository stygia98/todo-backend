package com.example.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 첨부 이미지 업로드 URL 발급 요청.
 *
 * <p>{@code fileSize}는 클라이언트 주장값이다. presign 단계에서는 상한 검사에만 쓰고,
 * complete 단계에서 {@code StorageService.verifyUploaded}가 돌려주는 실측값으로 갱신한다.
 */
public record AttachmentPresignRequest(

        @NotBlank(message = "파일명을 입력해 주세요.")
        String filename,

        @NotBlank(message = "파일 형식을 지정해 주세요.")
        String contentType,

        @NotNull(message = "파일 크기를 지정해 주세요.")
        @Positive(message = "파일 크기는 0보다 커야 합니다.")
        Long fileSize
) {
}
