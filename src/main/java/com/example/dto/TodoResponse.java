package com.example.dto;

import com.example.domain.Priority;
import com.example.domain.Todo;

import java.time.Instant;
import java.time.LocalDate;

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
 */
public record TodoResponse(
        Long id,
        String title,
        String content,
        boolean completed,
        Priority priority,
        LocalDate dueDate,
        Instant createdAt,
        Instant updatedAt
) {

    public static TodoResponse from(Todo todo) {
        return new TodoResponse(
                todo.getId(),
                todo.getTitle(),
                todo.getContent(),
                todo.isCompleted(),
                todo.getPriority(),
                todo.getDueDate(),
                todo.getCreatedAt(),
                todo.getUpdatedAt()
        );
    }
}
