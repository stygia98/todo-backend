package com.example.dto;

import com.example.domain.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Todo 수정 요청(전체 교체). 필드는 {@code title}, {@code content}, {@code priority},
 * {@code dueDate} 넷뿐이다.
 *
 * <p>⚠️ <b>{@code completed} 를 여기에 넣지 않는다.</b> 완료 상태는 오직
 * {@link TodoToggleRequest}(PATCH /toggle)로만 바꾼다. PUT 에 {@code completed} 가 있으면
 * 상세 화면에서 저장할 때마다 완료 상태를 덮어써, 목록에서 체크한 결과가 되돌아가는
 * 버그가 난다(CLAUDE.md 5장). 편의상 넣고 싶어지는 지점이라 의도적으로 뺐다는 점을 명시한다.
 *
 * <p>PUT 은 부분 수정이 아니라 전체 교체다. {@code title} 은 필수이므로 누락 시 400이고,
 * {@code content}·{@code dueDate} 는 누락하면 null로 저장된다(값 삭제로 취급).
 */
public record TodoUpdateRequest(

        @NotBlank(message = "제목을 입력해 주세요.")
        @Size(max = 200, message = "제목은 200자를 넘을 수 없습니다.")
        String title,

        @Size(max = 50_000, message = "본문은 50,000자를 넘을 수 없습니다.")
        String content,

        Priority priority,

        LocalDate dueDate
) {
}
