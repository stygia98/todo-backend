package com.example.dto;

import com.example.domain.Priority;
import com.example.domain.Todo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Todo 응답.
 *
 * <p>⚠️ <b>사용자 정보를 넣지 않는다.</b> 본인 데이터만 조회하므로 불필요하고, 넣으면
 * {@code Todo.user} 조회가 항목마다 따라붙어 목록에서 N+1이 난다. 편의상 넣고 싶어지는
 * 지점이라 의도적으로 뺐다는 점을 명시한다(CLAUDE.md 4장).
 *
 * <p>{@code createdAt}·{@code updatedAt} 은 {@link Instant} 그대로 반환한다. Boot 4의 Jackson 3
 * 기본값이 이미 ISO-8601 UTC 문자열(예: {@code 2026-08-28T04:30:00Z})로 직렬화하므로 별도 설정이
 * 필요 없다. {@code dueDate} 는 {@link LocalDate} 라 시각 없이 {@code yyyy-MM-dd} 로 나간다.
 *
 * <p>{@code attachments}는 저장 시점에 이미 {@code todo_id}로 연결된 첨부 목록이다(Phase 12).
 * 별도의 일괄 조회 엔드포인트를 두지 않는다 — 본문 캐시와 URL 캐시가 따로 만료되는 상태를
 * 피하기 위해 이 응답에 함께 담는다. {@code viewUrl}은 만료되는 서명 토큰을 포함하므로
 * 저장되는 본문 HTML의 {@code src}와는 무관한 표시 전용 값이다(6장 참조).
 */
public record TodoResponse(
        Long id,
        String title,
        String content,
        boolean completed,
        Priority priority,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt,
        List<AttachmentView> attachments
) {

    /** 첨부 1건의 표시용 정보. {@code id}는 {@code data-attachment-id}와 대응한다. */
    public record AttachmentView(Long id, String viewUrl) {
    }

    public static TodoResponse from(Todo todo, List<AttachmentView> attachments) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getContent(),
                todo.isCompleted(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.getCreatedAt(),
                todo.getUpdatedAt(),
                attachments
        );
    }
}
