package com.example.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 완료 상태 토글 요청.
 *
 * <p>⚠️ 서버가 현재 값을 뒤집지 않는다. 바디로 <b>목표 상태를 그대로</b> 받는다. 멱등해지므로
 * 낙관적 업데이트에서 요청 순서가 뒤바뀌어도(연타) 최종 상태가 서버와 어긋나지 않는다.
 *
 * <p>⚠️ {@code completed} 를 primitive {@code boolean} 이 아니라 래퍼 {@link Boolean} 으로 받는다.
 * primitive 를 쓰면 필드가 요청 본문에서 누락돼도 Jackson 이 조용히 {@code false} 로 채워,
 * "완료 처리해 달라"는 의도 없는 요청이 미완료로 처리될 수 있다. {@code @NotNull} 로 누락 자체를
 * 400으로 거부한다.
 */
public record TodoToggleRequest(

        @NotNull(message = "completed 값을 입력해 주세요.")
        Boolean completed
) {
}
