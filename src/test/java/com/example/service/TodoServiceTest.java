package com.example.service;

import com.example.domain.Priority;
import com.example.domain.Todo;
import com.example.domain.TodoRepository;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.TodoCreateRequest;
import com.example.dto.TodoUpdateRequest;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TodoService} 단위 테스트.
 *
 * <p>Repository 계층의 {@code search} 쿼리 자체는 {@code TodoRepositoryTest} 가 이미 검증한다.
 * 여기서는 정렬 화이트리스트·소유권 검증·toggle 멱등성처럼 <b>서비스 계층에서만 확인 가능한</b>
 * 동작을 다룬다. 정상 값만으로는 화이트리스트 누락이 드러나지 않으므로, 잘못된 값을 실제로
 * 넣어보는 테스트를 반드시 포함한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("TodoService 단위 테스트")
class TodoServiceTest {

    @Autowired
    private TodoService todoService;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    private User createUser(String email) {
        return userRepository.save(User.createLocal(email, "{bcrypt}hash", "테스터"));
    }

    /**
     * DoD 6 — {@code ?sort=foo,desc} 같은 잘못된 값이 들어와도 500이 나지 않아야 한다.
     * 존재하지 않는 프로퍼티를 그대로 {@code Pageable} 에 넘기면 Hibernate 가 예외를 던진다.
     */
    @Test
    @DisplayName("정렬 화이트리스트에 없는 값은 기본값(createdAt desc)으로 대체되어 예외가 나지 않는다")
    void invalidSortFallsBackToDefault() {
        User user = createUser("sort-invalid@example.com");
        todoService.create(user, new TodoCreateRequest("첫 번째", null, null, null));
        todoService.create(user, new TodoCreateRequest("두 번째", null, null, null));

        var invalidSortPageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "title"));

        Page<Todo> page = todoService.list(user.getId(), null, null, invalidSortPageable);

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("허용된 정렬 필드(dueDate)는 그대로 적용된다")
    void allowedSortIsApplied() {
        User user = createUser("sort-valid@example.com");
        todoService.create(user, new TodoCreateRequest("A", null, null, java.time.LocalDate.of(2026, 1, 1)));
        todoService.create(user, new TodoCreateRequest("B", null, null, java.time.LocalDate.of(2026, 12, 31)));

        var pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "dueDate"));

        Page<Todo> page = todoService.list(user.getId(), null, null, pageable);

        assertThat(page.getContent()).extracting(Todo::getTitle).containsExactly("A", "B");
    }

    @Test
    @DisplayName("타인의 Todo를 조회하면 404 TODO_NOT_FOUND를 던진다")
    void ownershipViolationThrows404() {
        User owner = createUser("owner@example.com");
        User stranger = createUser("stranger@example.com");
        Todo todo = todoService.create(owner, new TodoCreateRequest("내 것", null, null, null));

        assertThatThrownBy(() -> todoService.get(todo.getId(), stranger.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    @DisplayName("존재하지 않는 id 조회도 404 TODO_NOT_FOUND를 던진다")
    void nonExistentIdThrows404() {
        User user = createUser("nobody@example.com");

        assertThatThrownBy(() -> todoService.get(999_999L, user.getId()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TODO_NOT_FOUND);
    }

    @Test
    @DisplayName("update는 completed를 건드리지 않는다 (PUT이 완료 상태를 덮어쓰지 않음)")
    void updateDoesNotOverwriteCompleted() {
        User user = createUser("put-completed@example.com");
        Todo todo = todoService.create(user, new TodoCreateRequest("제목", null, null, null));
        todoService.toggle(todo.getId(), user.getId(), true);

        todoService.update(todo.getId(), user.getId(),
                new TodoUpdateRequest("바뀐 제목", null, Priority.HIGH, null));

        assertThat(todo.isCompleted()).isTrue();
        assertThat(todo.getTitle()).isEqualTo("바뀐 제목");
    }

    @Test
    @DisplayName("toggle은 서버가 값을 뒤집지 않고 요청받은 목표 상태를 그대로 반영한다 (멱등)")
    void toggleReflectsRequestedValueIdempotently() {
        User user = createUser("toggle-service@example.com");
        Todo todo = todoService.create(user, new TodoCreateRequest("토글 대상", null, null, null));

        todoService.toggle(todo.getId(), user.getId(), true);
        todoService.toggle(todo.getId(), user.getId(), true);

        assertThat(todo.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("delete는 물리 삭제가 아니라 softDelete를 호출한다")
    void deleteCallsSoftDelete() {
        User user = createUser("delete@example.com");
        Todo todo = todoService.create(user, new TodoCreateRequest("삭제 대상", null, null, null));

        todoService.delete(todo.getId(), user.getId());

        assertThat(todoRepository.findByIdAndDeletedAtIsNull(todo.getId())).isEmpty();
        assertThat(todoRepository.findById(todo.getId())).isPresent();
    }

    @Test
    @DisplayName("content는 저장 전 HtmlSanitizer로 정화된다 (script 태그 제거)")
    void contentIsSanitizedOnCreate() {
        User user = createUser("xss@example.com");

        Todo todo = todoService.create(user,
                new TodoCreateRequest("XSS 확인", "<p>안전</p><script>alert(1)</script>", null, null));

        assertThat(todo.getContent()).doesNotContain("<script>").contains("<p>안전</p>");
    }
}
