package com.example.service;

import com.example.domain.Todo;
import com.example.domain.TodoRepository;
import com.example.domain.TodoSpecifications;
import com.example.domain.User;
import com.example.dto.TodoCreateRequest;
import com.example.dto.TodoUpdateRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Todo 생성·조회·수정·토글·삭제.
 *
 * <p>소유권 검증({@link #findOwned})과 정렬 화이트리스트({@link #sanitizeSort})를
 * 이 계층에서 한 지점으로 모은다. 컨트롤러는 DTO 변환만 하고 이 서비스를 거치지 않고는
 * {@link Todo} 에 접근하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TodoService {

    /**
     * 정렬 허용 필드. 서비스 진입부에서 걸러야 하는 이유는, 컨트롤러에 두면 {@code Pageable}
     * 이 이미 만들어진 뒤라 검증이 늦고, 이 서비스를 직접 호출하는 테스트가 보호받지 못하기
     * 때문이다. 여기 없는 프로퍼티를 그대로 리포지토리에 넘기면 없는 프로퍼티로 500 이 난다.
     */
    private static final Set<String> ALLOWED_SORT_PROPERTIES = Set.of("createdAt", "dueDate");

    private final TodoRepository todoRepository;
    private final HtmlSanitizer htmlSanitizer;

    /**
     * 목록 조회. {@code completed} 가 {@code null} 이면 전체, {@code keyword} 가 {@code null} 이면
     * 검색 없음.
     *
     * <p>JPQL {@code @Query} 대신 {@link Specification} 을 쓴다. 이유는
     * {@link TodoSpecifications} 클래스 javadoc 참조 — named parameter 의 PostgreSQL 타입 추론
     * 문제를 근본적으로 피한다.
     */
    public Page<Todo> list(Long userId, Boolean completed, String keyword, Pageable pageable) {
        Specification<Todo> spec = Specification.allOf(
                TodoSpecifications.ownedBy(userId),
                TodoSpecifications.completedIs(completed),
                TodoSpecifications.titleContains(keyword)
        );
        return todoRepository.findAll(spec, sanitizeSort(pageable));
    }

    @Transactional
    public Todo create(User user, TodoCreateRequest request) {
        String sanitizedContent = htmlSanitizer.clean(request.content());
        Todo todo = Todo.create(user, request.title(), sanitizedContent,
                request.priority(), request.dueDate());
        return todoRepository.save(todo);
    }

    public Todo get(Long id, Long userId) {
        return findOwned(id, userId);
    }

    /**
     * 전체 교체(PUT). {@code completed} 를 건드리지 않는다 — {@link Todo#update} 시그니처에
     * 애초에 그 파라미터가 없어, 완료 상태가 여기서 덮어써질 여지가 엔티티 수준에서 막혀 있다.
     */
    @Transactional
    public Todo update(Long id, Long userId, TodoUpdateRequest request) {
        Todo todo = findOwned(id, userId);
        String sanitizedContent = htmlSanitizer.clean(request.content());
        todo.update(request.title(), sanitizedContent, request.priority(), request.dueDate());
        return todo;
    }

    /**
     * 완료 상태 토글. 서버가 현재 값을 뒤집지 않고 요청받은 목표 상태를 그대로 반영한다.
     * 같은 값으로 두 번 호출해도 결과가 같다(멱등).
     */
    @Transactional
    public Todo toggle(Long id, Long userId, boolean completed) {
        Todo todo = findOwned(id, userId);
        todo.updateCompleted(completed);
        return todo;
    }

    /** Soft Delete. 물리 삭제하지 않는다. */
    @Transactional
    public void delete(Long id, Long userId) {
        Todo todo = findOwned(id, userId);
        todo.softDelete();
    }

    /**
     * 조회 + 소유권 검증을 한 지점으로 모은다.
     *
     * <p>소유권 불일치는 403 이 아니라 404 다. 403 은 "존재하지만 권한이 없다"는 사실을
     * 알려주므로 리소스 존재 여부가 새어나간다(CLAUDE.md 6장). {@code getUser().getId()} 는
     * {@code Todo.user} 가 LAZY 프록시라도 id 만 읽으므로 추가 쿼리를 유발하지 않는다.
     */
    private Todo findOwned(Long id, Long userId) {
        Todo todo = todoRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TODO_NOT_FOUND));
        if (!todo.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.TODO_NOT_FOUND);
        }
        return todo;
    }

    /**
     * 정렬 값을 화이트리스트로 거른다. 허용되지 않은 프로퍼티는 전부 제거되고,
     * 남는 것이 없으면 {@code createdAt} 내림차순으로 대체한다.
     */
    private Pageable sanitizeSort(Pageable pageable) {
        var orders = pageable.getSort().stream()
                .filter(order -> ALLOWED_SORT_PROPERTIES.contains(order.getProperty()))
                .toList();

        Sort sort = orders.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "createdAt")
                : Sort.by(orders);

        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }
}
