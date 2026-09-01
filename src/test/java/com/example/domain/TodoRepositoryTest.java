package com.example.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TodoRepository} 단위 테스트와 타임존 저장 검증.
 *
 * <p>애노테이션 조합의 이유는 {@link UserRepositoryTest} 참조.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User createUser(String email) {
        return userRepository.save(User.createLocal(email, "{bcrypt}hash", "테스터"));
    }

    @Test
    @DisplayName("사용자별 할 일 목록을 페이지로 조회한다")
    void findByUserId() {
        User user = createUser("owner@example.com");
        todoRepository.save(Todo.create(user, "첫 번째", null, Priority.HIGH, LocalDate.of(2026, 9, 1)));
        todoRepository.save(Todo.create(user, "두 번째", "<p>본문</p>", null, null));

        Page<Todo> page = todoRepository.findByUserIdAndDeletedAtIsNull(user.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Todo::getTitle)
                .containsExactlyInAnyOrder("첫 번째", "두 번째");
    }

    @Test
    @DisplayName("priority 를 지정하지 않으면 MEDIUM 이 된다")
    void defaultPriorityIsMedium() {
        User user = createUser("default@example.com");

        Todo saved = todoRepository.save(Todo.create(user, "우선순위 미지정", null, null, null));

        assertThat(saved.getPriority()).isEqualTo(Priority.MEDIUM);
        assertThat(saved.isCompleted()).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 할 일은 목록에서 제외된다")
    void softDeletedTodoIsExcluded() {
        User user = createUser("del@example.com");
        Todo keep = todoRepository.save(Todo.create(user, "남길 것", null, null, null));
        Todo drop = todoRepository.save(Todo.create(user, "지울 것", null, null, null));

        drop.softDelete();
        entityManager.flush();
        entityManager.clear();

        Page<Todo> page = todoRepository.findByUserIdAndDeletedAtIsNull(user.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(keep.getId());
        assertThat(todoRepository.findByIdAndDeletedAtIsNull(drop.getId())).isEmpty();
        // 물리 삭제가 아니므로 행 자체는 남아 있다
        assertThat(todoRepository.findById(drop.getId())).isPresent();
    }

    @Test
    @DisplayName("타인 소유의 할 일은 조회되지 않는다")
    void otherUsersTodoIsNotVisible() {
        User owner = createUser("me@example.com");
        User stranger = createUser("other@example.com");
        todoRepository.save(Todo.create(owner, "내 할 일", null, null, null));

        Page<Todo> page = todoRepository.findByUserIdAndDeletedAtIsNull(stranger.getId(), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isZero();
    }

    /**
     * Phase 4 착수 시 확인되지 않았던 리스크의 실측이다.
     *
     * <p>애초에 {@code @Query}("... :completed IS NULL OR ...")로 만들었다가
     * {@link TodoSpecifications} 로 전환했다. PostgreSQL 이 named parameter 의 타입을
     * SQL 텍스트 <b>첫 실행 시점의 문맥</b>으로 추론하는데, {@code null} 이 최초 바인딩이면
     * 문맥이 없어 {@code bytea} 로 기본 처리돼 {@code lower(bytea)} 나 {@code bytea→boolean}
     * 캐스팅 오류가 났다({@code TodoServiceTest} 에서 재현, 2026-09-01). JPQL 안에서
     * {@code CAST(:param AS ...)} 를 추가해도 소용없었다 — pgjdbc 가 파라미터를 바인딩하는
     * 시점의 와이어 타입 자체가 문제였기 때문이다. Criteria 기반 {@link Specification} 은
     * 컬럼의 실제 타입을 메타모델에서 알고 명시적으로 바인딩하므로 이 문제가 없다.
     */
    @Test
    @DisplayName("Specification — completed 가 null 이면 전체가 조회된다 (PostgreSQL 파라미터 타입 추론 검증)")
    void searchWithNullCompletedReturnsAll() {
        User user = createUser("search-null@example.com");
        todoRepository.save(Todo.create(user, "완료된 것", null, null, null)).updateCompleted(true);
        todoRepository.save(Todo.create(user, "미완료인 것", null, null, null));
        entityManager.flush();
        entityManager.clear();

        Page<Todo> page = todoRepository.findAll(searchSpec(user.getId(), null, null), PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("Specification — completed 를 true/false 로 지정하면 필터가 적용된다")
    void searchWithCompletedFilters() {
        User user = createUser("search-filter@example.com");
        Todo done = todoRepository.save(Todo.create(user, "완료된 것", null, null, null));
        todoRepository.save(Todo.create(user, "미완료인 것", null, null, null));
        done.updateCompleted(true);
        entityManager.flush();
        entityManager.clear();

        Page<Todo> completedOnly = todoRepository.findAll(searchSpec(user.getId(), true, null), PageRequest.of(0, 10));
        Page<Todo> pendingOnly = todoRepository.findAll(searchSpec(user.getId(), false, null), PageRequest.of(0, 10));

        assertThat(completedOnly.getContent()).extracting(Todo::getTitle).containsExactly("완료된 것");
        assertThat(pendingOnly.getContent()).extracting(Todo::getTitle).containsExactly("미완료인 것");
    }

    @Test
    @DisplayName("Specification — 대소문자를 섞은 키워드로도 검색된다")
    void searchIsCaseInsensitive() {
        User user = createUser("search-case@example.com");
        todoRepository.save(Todo.create(user, "Spring Boot 학습", null, null, null));
        todoRepository.save(Todo.create(user, "무관한 항목", null, null, null));
        entityManager.flush();
        entityManager.clear();

        Page<Todo> page = todoRepository.findAll(
                searchSpec(user.getId(), null, "SPRING boot"), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(Todo::getTitle).containsExactly("Spring Boot 학습");
    }

    private Specification<Todo> searchSpec(Long userId, Boolean completed, String keyword) {
        return Specification.allOf(
                TodoSpecifications.ownedBy(userId),
                TodoSpecifications.completedIs(completed),
                TodoSpecifications.titleContains(keyword)
        );
    }

    @Test
    @DisplayName("완료 상태는 목표 값을 그대로 반영한다")
    void updateCompletedIsIdempotent() {
        User user = createUser("toggle@example.com");
        Todo todo = todoRepository.save(Todo.create(user, "토글", null, null, null));

        // 현재 값을 뒤집는 방식이 아니므로 같은 값을 두 번 넣어도 결과가 같다
        todo.updateCompleted(true);
        todo.updateCompleted(true);

        assertThat(todo.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("저장 시 created_at 과 updated_at 이 자동으로 기록된다")
    void auditingFieldsArePopulated() {
        User user = createUser("audit2@example.com");

        Todo saved = todoRepository.save(Todo.create(user, "감사 확인", null, null, null));

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    /**
     * DoD 6 — 저장된 시각이 UTC 기준인지 검증한다.
     *
     * <p>⚠️ <b>애플리케이션이 만든 값끼리 비교하면 안 된다.</b>
     * {@code saved.getCreatedAt()} 과 {@code Instant.now()} 를 비교하면 둘 다 같은 코드 경로라
     * 어떤 설정에서도 통과해 회귀를 전혀 잡지 못한다.
     *
     * <p>그래서 네이티브 쿼리로 <b>DB에 실제로 기록된 벽시계 값</b>을 읽어온다.
     * 이때 컬럼을 그대로 select 하면 드라이버가 {@code Instant} 로 변환해 돌려주는데,
     * 그 변환 자체가 타임존 설정을 한 번 더 태우므로 원본을 볼 수 없다.
     * <b>{@code CAST(... AS text)} 로 DB에서 문자열로 만들어</b> 변환 경로를 통째로 우회한다.
     */
    @Test
    @DisplayName("created_at 이 KST 가 아니라 UTC 기준으로 저장된다")
    void createdAtIsStoredInUtc() {
        User user = createUser("tz@example.com");
        Todo saved = todoRepository.save(Todo.create(user, "타임존 확인", null, null, null));
        entityManager.flush();

        // DB가 UTC로 변환해 문자열로 만들어 주므로 드라이버의 타입 변환을 거치지 않는다.
        // AT TIME ZONE 'UTC' 를 붙이지 않으면 세션 타임존(개발 환경은 Asia/Seoul)으로 렌더링되어
        // +09 오프셋이 붙은 문자열이 나온다. 값 자체는 같지만 표기가 달라 비교가 번거로워진다.
        Object raw = entityManager
                .createNativeQuery("SELECT CAST(created_at AT TIME ZONE 'UTC' AS text) FROM todos WHERE id = :id")
                .setParameter("id", saved.getId())
                .getSingleResult();

        LocalDateTime storedUtc = parseWallClock(raw);
        LocalDateTime utcNow = LocalDateTime.now(ZoneOffset.UTC);

        // DB에 기록된 시각을 UTC로 환산한 값이 UTC 현재 시각과 사실상 같아야 한다.
        // 애플리케이션이 KST 벽시계를 UTC인 양 보냈다면 여기서 9시간 차이로 드러난다.
        assertThat(Duration.between(storedUtc, utcNow).abs())
                .as("created_at 이 UTC 기준으로 저장되어야 한다. DB 저장값(UTC 환산)=%s, UTC 현재=%s",
                        storedUtc, utcNow)
                .isLessThan(Duration.ofMinutes(5));
    }

    /**
     * 감사 컬럼이 {@code timestamp with time zone} 으로 매핑되는지 확인한다.
     *
     * <p>{@code Instant} 는 Hibernate 에서 {@code timestamptz} 로 매핑되며,
     * PostgreSQL 의 {@code timestamptz} 는 <b>내부적으로 항상 UTC 로 저장</b>하고
     * 조회 시 세션 타임존으로 렌더링한다. 즉 이 타입인 한 세션이나 JVM 타임존이 무엇이든
     * 저장된 시점이 흔들리지 않는다. UTC 보장의 구조적 근거이므로 타입 자체를 고정한다.
     */
    @Test
    @DisplayName("감사 컬럼이 timestamptz 로 매핑된다")
    void auditColumnsAreTimestampTz() {
        Object type = entityManager
                .createNativeQuery("""
                        SELECT data_type FROM information_schema.columns
                        WHERE table_name = 'todos' AND column_name = 'created_at'
                        """)
                .getSingleResult();

        assertThat(String.valueOf(type)).isEqualTo("timestamp with time zone");
    }

    /**
     * DB가 돌려준 텍스트를 그대로 파싱한다.
     *
     * <p>PostgreSQL 은 {@code 2026-08-28 09:00:00.123456} 형태로 내보내므로
     * 공백을 {@code T} 로 바꾸면 ISO-8601 로컬 형식이 된다.
     */
    private LocalDateTime parseWallClock(Object raw) {
        if (raw instanceof String text) {
            return LocalDateTime.parse(text.trim().replace(' ', 'T'));
        }
        throw new IllegalStateException("created_at 텍스트 캐스팅 실패. 실제 타입: " + raw.getClass());
    }
}
