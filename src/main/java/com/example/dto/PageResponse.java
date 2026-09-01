package com.example.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 페이지 응답. CLAUDE.md 5장의 목록 응답 형태와 1:1로 대응한다.
 *
 * <p>Spring 의 {@link Page} 를 그대로 반환하지 않는다. 직렬화 형태가 스펙과 다르고,
 * {@code pageable}·{@code sort} 등 내부 구현이 응답에 새어나간다.
 *
 * <p>⚠️ 이 레코드 자체가 최상위 응답이 아니다. {@code ApiResponse.data} 안에 담겨 나간다.
 * 프론트는 {@code res.data.data.content} 로 접근한다(CLAUDE.md 5장).
 *
 * @param page 0부터 시작하는 현재 페이지 번호
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    /** 엔티티 {@link Page} 를 응답 DTO로 변환하며 원소도 함께 매핑한다. */
    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }
}
