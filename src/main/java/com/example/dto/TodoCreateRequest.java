package com.example.dto;

import com.example.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Todo 생성 요청. CLAUDE.md 4장 입력값 제약 표와 1:1로 대응한다.
 *
 * <p>{@code content} 는 선택 항목이라 {@code @NotBlank} 를 걸지 않는다. 최대 50,000자만 제한한다 —
 * TEXT 컬럼은 길이 제한이 없어 대용량 붙여넣기로 요청이 터질 수 있기 때문이다.
 *
 * <p>{@code priority} 는 String 이 아니라 {@link Priority} 로 받는다. 잘못된 값은 Jackson 역직렬화
 * 단계에서 걸러져 {@code HttpMessageNotReadableException} → 400으로 응답된다({@code null} 이면
 * {@code Todo.create} 가 MEDIUM 으로 채운다).
 */
public record TodoCreateRequest(

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Size(max = 50_000, message = "본문은 50,000자를 넘을 수 없습니다.")
        String content,

        Priority priority,

        LocalDate dueDate
) {
}
