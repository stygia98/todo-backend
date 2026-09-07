package com.example.dto;

/** 첨부 조회용 URL 응답. {@code complete}와 {@code /url} 두 엔드포인트가 함께 쓴다. */
public record AttachmentViewResponse(
        Long id,
        String viewUrl
) {
}
