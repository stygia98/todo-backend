package com.example.domain;

import org.springframework.data.jpa.domain.Specification;

/**
 * {@link Todo} 목록 조회의 동적 조건.
 *
 * <p>⚠️ <b>애초에 JPQL {@code @Query} 로 만들었다가 이 방식으로 전환했다.</b>
 * {@code :param IS NULL OR t.column = :param} 형태의 named parameter 는 PostgreSQL 이
 * bind parameter 의 타입을 <b>SQL 텍스트의 첫 실행 시점 문맥</b>으로 추론한다. {@code null} 이
 * 최초로 바인딩되면 문맥이 없어 {@code bytea} 로 기본 처리되고, 이를 {@code LOWER()} 나
 * {@code boolean} 비교에 넘기면 타입 오류가 난다. {@code CAST(:param AS ...)} 를 SQL에 추가해도
 * 소용없었다 — pgjdbc 가 파라미터를 <b>바인딩하는 시점의 와이어 타입 자체</b>가 문제이지,
 * SQL 안에서의 형변환으로 고쳐지는 문제가 아니었다(2026-09-01 실측, {@code TodoServiceTest} 에서
 * 재현). Criteria API 는 Hibernate 가 메타모델에서 컬럼의 실제 타입(boolean, varchar)을 이미 알고
 * {@code PreparedStatement} 에 명시적 SQL 타입으로 바인딩하므로, 이 클래스의 조건들은
 * 이 문제 자체가 발생하지 않는다. 스택 추가 없이 Spring Data JPA 기본 기능만 쓴다.
 */
public final class TodoSpecifications {

    private TodoSpecifications() {
    }

    /** 소유자 + 미삭제. 모든 목록 조회에 항상 포함된다. */
    public static Specification<Todo> ownedBy(Long userId) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("user").get("id"), userId),
                cb.isNull(root.get("deletedAt"))
        );
    }

    /** {@code completed} 가 {@code null} 이면 조건을 걸지 않는다(전체 반환). */
    public static Specification<Todo> completedIs(Boolean completed) {
        if (completed == null) {
            return Specification.unrestricted();
        }
        return (root, query, cb) -> cb.equal(root.get("completed"), completed);
    }

    /**
     * {@code keyword} 가 {@code null}이거나 공백이면 조건을 걸지 않는다.
     *
     * <p>대소문자 무시를 위해 컬럼과 파라미터 양쪽에 {@code lower} 를 건다. 컬럼만 걸면
     * 대문자가 섞인 키워드가 매칭되지 않는다.
     */
    public static Specification<Todo> titleContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Specification.unrestricted();
        }
        String pattern = "%" + keyword.toLowerCase() + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("title")), pattern);
    }
}
