package com.example.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 할 일 조회.
 *
 * <p>⚠️ <b>모든 조회 메서드에 {@code AndDeletedAtIsNull} 을 붙인다.</b>
 * 빠뜨리면 소프트 삭제한 항목이 목록에 다시 나타난다.
 *
 * <p>목록 조회는 인덱스 {@code idx_todos_user_deleted}({@code user_id, deleted_at})가 덮는다.
 *
 * <p>{@link JpaSpecificationExecutor} 를 확장해 완료 필터·제목 검색 조합 조회를 지원한다.
 * Specification 조립은 {@link com.example.domain.TodoSpecifications} 와
 * {@code TodoService} 를 참조한다. 이 인터페이스에 조합 쿼리를 직접 두지 않는 이유는
 * 아래 참조.
 */
public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    /**
     * 단건 조회.
     *
     * <p>소유권 검증은 서비스 계층에서 {@code todo.getUser().getId()} 와 인증 사용자 id 를 비교해 한다.
     * 불일치 시 403 이 아니라 <b>404</b> 를 반환한다(존재 여부 노출 방지).
     */
    Optional<Todo> findByIdAndDeletedAtIsNull(Long id);

    /** 사용자별 목록(필터 없음). Phase 2 의 {@code TodoRepositoryTest} 가 이 시그니처를 쓰므로 유지한다. */
    Page<Todo> findByUserIdAndDeletedAtIsNull(Long userId, Pageable pageable);
}
