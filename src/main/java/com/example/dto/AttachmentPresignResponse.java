package com.example.dto;

/**
 * 업로드 URL 발급 응답.
 *
 * <p>{@code contentType}을 그대로 되돌려준다. S3 presigned PUT은 서명에 Content-Type이
 * 포함되어 클라이언트가 보내는 헤더가 바이트 단위로 같지 않으면 서명 불일치가 나므로,
 * 서버가 정규화한 값을 프론트가 업로드 PUT 헤더에 그대로 써야 한다(Phase 14 대비).
 */
public record AttachmentPresignResponse(
        Long attachmentId,
        String uploadUrl,
        String storageKey,
        String contentType
) {
}
