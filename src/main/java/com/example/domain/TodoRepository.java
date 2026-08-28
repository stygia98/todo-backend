package com.example.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 할 일 조회.
 *
 * <p>⚠️ <b>모든 조회 메서드에 {@code AndDeletedAtIsNull} 을 붙인다.</b>
 * 빠뜨리면 소프트 삭제한 항목이 목록에 다시 나타난다.
 *
 * <p>목록 조회는 인덱스 {@code idx_todos_user_deleted}({@code user_id, deleted_at})가 덮는다.
 *
 * <p><b>이 인터페이스는 Phase 2 의 기본 조회형만 담는다.</b>
 * 완료 여부 필터와 제목 검색을 조합한 쿼리는 Phase 4 에서 파라미터 조합이 확정된 뒤 추가한다.
 * 미리 만들면 시그니처가 바뀌어 재작업이 된다.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    /**
     * 단건 조회.
     *
     * <p>소유권 검증은 서비스 계층에서 {@code todo.getUser().getId()} 와 인증 사용자 id 를 비교해 한다.
     * 불일치 시 403 이 아니라 <b>404</b> 를 반환한다(존재 여부 노출 방지).
     */
    Optional<Todo> findByIdAndDeletedAtIsNull(Long id);

    /** 사용자별 목록. 정렬과 페이지 크기는 {@code Pageable} 로 받는다. */
    Page<Todo> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);
}
