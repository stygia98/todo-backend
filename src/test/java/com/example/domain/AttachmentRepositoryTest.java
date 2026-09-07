package com.example.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AttachmentRepository} 단위 테스트.
 *
 * <p>애노테이션 조합의 이유는 {@link TodoRepositoryTest} 참조.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AttachmentRepositoryTest {

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    private User createUser(String email) {
        return userRepository.save(User.createLocal(email, "{bcrypt}hash", "테스터"));
    }

    private Attachment createTemp(User user) {
        return attachmentRepository.save(Attachment.createTemp(
                user, StorageType.LOCAL, "todos/1/2026/09/" + java.util.UUID.randomUUID() + ".png",
                "photo.png", "image/png", 1024L));
    }

    @Test
    @DisplayName("업로드 직후에는 TEMP 상태이고 todo 가 없다")
    void createTempStartsUnlinked() {
        User user = createUser("upload@example.com");

        Attachment saved = createTemp(user);

        assertThat(saved.getStatus()).isEqualTo(AttachmentStatus.TEMP);
        assertThat(saved.getTodo()).isNull();
    }

    @Test
    @DisplayName("linkTo 호출 시 LINKED 로 전환되고 todo 가 채워진다")
    void linkToTransitionsToLinked() {
        User user = createUser("link@example.com");
        Todo todo = todoRepository.save(Todo.create(user, "본문", "<p>내용</p>", null, null));
        Attachment attachment = createTemp(user);

        attachment.linkTo(todo);
        entityManager.flush();
        entityManager.clear();

        Attachment reloaded = attachmentRepository.findById(attachment.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(AttachmentStatus.LINKED);
        assertThat(reloaded.getTodo().getId()).isEqualTo(todo.getId());
    }

    @Test
    @DisplayName("markUploaded 는 실측 파일 크기로 갱신한다")
    void markUploadedUpdatesFileSize() {
        User user = createUser("size@example.com");
        Attachment attachment = createTemp(user);

        attachment.markUploaded(2048L);

        assertThat(attachment.getFileSize()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("소프트 삭제된 첨부는 findByIdAndDeletedAtIsNull 에서 제외된다")
    void softDeletedAttachmentIsExcludedButRowRemains() {
        User user = createUser("delete@example.com");
        Attachment attachment = createTemp(user);

        attachment.softDelete();
        entityManager.flush();
        entityManager.clear();

        assertThat(attachmentRepository.findByIdAndDeletedAtIsNull(attachment.getId())).isEmpty();
        // 물리 삭제가 아니므로 행 자체는 남아 있다
        assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
    }

    @Test
    @DisplayName("여러 Todo 에 연결된 첨부를 한 번에 배치 조회한다 (N+1 방지)")
    void findByTodoIdInReturnsAttachmentsAcrossMultipleTodos() {
        User user = createUser("batch@example.com");
        Todo todoA = todoRepository.save(Todo.create(user, "A", null, null, null));
        Todo todoB = todoRepository.save(Todo.create(user, "B", null, null, null));
        Todo todoC = todoRepository.save(Todo.create(user, "C(연결 안 됨)", null, null, null));

        Attachment onA = createTemp(user);
        onA.linkTo(todoA);
        Attachment onB = createTemp(user);
        onB.linkTo(todoB);
        entityManager.flush();
        entityManager.clear();

        List<Attachment> result = attachmentRepository.findByTodoIdInAndDeletedAtIsNull(
                List.of(todoA.getId(), todoB.getId(), todoC.getId()));

        assertThat(result).extracting(a -> a.getTodo().getId())
                .containsExactlyInAnyOrder(todoA.getId(), todoB.getId());
    }

    @Test
    @DisplayName("배치 조회에서도 소프트 삭제된 첨부는 제외된다")
    void findByTodoIdInExcludesSoftDeleted() {
        User user = createUser("batch-delete@example.com");
        Todo todo = todoRepository.save(Todo.create(user, "본문", null, null, null));
        Attachment kept = createTemp(user);
        kept.linkTo(todo);
        Attachment dropped = createTemp(user);
        dropped.linkTo(todo);
        dropped.softDelete();
        entityManager.flush();
        entityManager.clear();

        List<Attachment> result = attachmentRepository.findByTodoIdInAndDeletedAtIsNull(List.of(todo.getId()));

        assertThat(result).extracting(Attachment::getId).containsExactly(kept.getId());
    }

    @Test
    @DisplayName("저장 시 created_at 과 updated_at 이 자동으로 기록된다")
    void auditingFieldsArePopulated() {
        User user = createUser("audit@example.com");

        Attachment saved = createTemp(user);

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
