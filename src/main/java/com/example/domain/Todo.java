package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 할 일. CLAUDE.md 4장 {@code todos} 표와 1:1로 대응한다.
 *
 * <p>인덱스 {@code idx_todos_user_deleted} 는 목록 조회의 주 경로(사용자별 + 미삭제)를 덮는다.
 * 다만 제목 검색의 {@code LOWER(title) LIKE '%키워드%'} 는 앞쪽 와일드카드 때문에 인덱스를 타지 못한다.
 * MVP 데이터 규모에서는 문제없으며, {@code title} 에 인덱스를 추가해도 검색은 빨라지지 않는다.
 *
 * <p>{@code @Setter} 를 두지 않는다. 변경은 아래 세 메서드로만 한다.
 */
@Entity
@Getter
@Table(
        name = "todos",
        indexes = @Index(name = "idx_todos_user_deleted", columnList = "user_id, deleted_at")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Todo extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 소유자.
     *
     * <p>⚠️ {@code @ManyToOne} 의 기본값은 EAGER 다. 명시하지 않으면 목록 조회 시
     * 항목마다 사용자 조회가 따라붙어 N+1 이 된다. 반드시 LAZY 를 지정한다.
     *
     * <p>소유권 검증은 {@code todo.getUser().getId()} 로 한다.
     * 프록시 상태에서 id 만 읽으면 추가 쿼리가 발생하지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    /**
     * Tiptap 이 생성한 HTML. 저장 전 Jsoup Safelist 로 정화한다(Phase 4).
     *
     * <p>최대 50,000자 제한은 DTO 검증 단계에서 처리한다.
     * TEXT 는 길이 제한이 없어 대용량 붙여넣기로 요청이 터질 수 있기 때문이다.
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "completed", nullable = false)
    private boolean completed;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 10, nullable = false)
    private Priority priority;

    /** 마감일. 시각이 없어 타임존 영향을 받지 않으므로 {@code LocalDate} 를 쓴다. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    /** 소프트 삭제 시각(UTC). 물리 삭제를 하지 않는다. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Todo(User user, String title, String content, Priority priority, LocalDate dueDate) {
        this.user = user;
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
        this.completed = false;
    }

    /** 생성. {@code priority} 가 null 이면 MEDIUM 으로 채운다. */
    public static Todo create(User user, String title, String content,
                              Priority priority, LocalDate dueDate) {
        return new Todo(user, title, content, priority, dueDate);
    }

    /**
     * 내용 수정(전체 교체).
     *
     * <p>⚠️ {@code completed} 를 여기서 바꾸지 않는다. 완료 상태는 오직
     * {@link #updateCompleted(boolean)} 로만 변경한다. 이 메서드가 완료 상태까지 건드리면
     * 상세 화면에서 저장할 때마다 목록에서 체크한 결과가 되돌아가는 버그가 난다(CLAUDE.md 5장).
     */
    public void update(String title, String content, Priority priority, LocalDate dueDate) {
        this.title = title;
        this.content = content;
        this.priority = priority != null ? priority : Priority.MEDIUM;
        this.dueDate = dueDate;
    }

    /**
     * 완료 상태 변경.
     *
     * <p>현재 값을 뒤집지 않고 <b>목표 상태를 그대로 받는다.</b> 멱등해지므로
     * 낙관적 업데이트에서 요청 순서가 뒤바뀌어도 최종 상태가 일치한다.
     */
    public void updateCompleted(boolean completed) {
        this.completed = completed;
    }

    /** 소프트 삭제. 모든 조회에 {@code deleted_at IS NULL} 조건을 포함해야 한다. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
