package com.example.service;

import com.example.domain.Attachment;
import com.example.domain.AttachmentRepository;
import com.example.domain.AttachmentStatus;
import com.example.domain.StorageType;
import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.storage.LocalStorageService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link OrphanAttachmentCleaner} 통합 테스트.
 *
 * <p>{@code @CreatedDate}로 자동 채워지는 {@code created_at}과, 애초에 시각 조작 API가 없는
 * {@code deleted_at}은 엔티티 setter로 과거 값을 넣을 수 없다. 네이티브 UPDATE로 감사 필드를
 * 직접 되돌린 뒤 {@code entityManager.clear()}로 영속성 컨텍스트를 비워, 배치가 DB의 실제 값을
 * 다시 읽게 만든다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("고아 첨부 파일 정리 배치")
class OrphanAttachmentCleanerTest {

    @Autowired
    private OrphanAttachmentCleaner cleaner;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LocalStorageService localStorageService;

    @Autowired
    private EntityManager entityManager;

    private User createUser(String email) {
        return userRepository.save(User.createLocal(email, "{bcrypt}hash", "테스터"));
    }

    private Attachment createAttachmentWithFile(User user) {
        String storageKey = "orphan-test/" + UUID.randomUUID() + ".png";
        Attachment attachment = attachmentRepository.save(Attachment.createTemp(
                user, StorageType.LOCAL, storageKey, "photo.png", "image/png", 4L));
        localStorageService.write(storageKey, new ByteArrayInputStream(new byte[]{1, 2, 3, 4}));
        return attachment;
    }

    private void backdateCreatedAt(Long attachmentId, Instant when) {
        entityManager.createNativeQuery("UPDATE attachments SET created_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(when))
                .setParameter(2, attachmentId)
                .executeUpdate();
    }

    private void backdateDeletedAt(Long attachmentId, Instant when) {
        entityManager.createNativeQuery("UPDATE attachments SET deleted_at = ?1 WHERE id = ?2")
                .setParameter(1, Timestamp.from(when))
                .setParameter(2, attachmentId)
                .executeUpdate();
    }

    @Test
    @DisplayName("TEMP 상태로 24시간 넘은 첨부는 파일과 레코드가 모두 삭제된다")
    void expiredTempAttachmentIsFullyDeleted() {
        User user = createUser("orphan-temp@example.com");
        Attachment expired = createAttachmentWithFile(user);
        backdateCreatedAt(expired.getId(), Instant.now().minus(Duration.ofHours(25)));
        entityManager.flush();
        entityManager.clear();

        cleaner.cleanOrphans();

        assertThat(attachmentRepository.findById(expired.getId())).isEmpty();
        assertThatThrownBy(() -> localStorageService.read(expired.getStorageKey()))
                .as("실제 파일도 함께 삭제되어야 한다")
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    @DisplayName("24시간이 지나지 않은 TEMP 첨부는 그대로 남는다")
    void freshTempAttachmentIsUntouched() {
        User user = createUser("orphan-fresh@example.com");
        Attachment fresh = createAttachmentWithFile(user);
        entityManager.flush();
        entityManager.clear();

        cleaner.cleanOrphans();

        assertThat(attachmentRepository.findById(fresh.getId())).isPresent();
        assertThat(localStorageService.read(fresh.getStorageKey())).isNotEmpty();
    }

    @Test
    @DisplayName("소프트 삭제 7일이 지난 첨부는 파일만 삭제되고 레코드는 남는다")
    void oldSoftDeletedAttachmentKeepsRecordButDeletesFile() {
        User user = createUser("orphan-old-deleted@example.com");
        Attachment attachment = createAttachmentWithFile(user);
        attachment.softDelete();
        attachmentRepository.save(attachment);
        backdateDeletedAt(attachment.getId(), Instant.now().minus(Duration.ofDays(8)));
        entityManager.flush();
        entityManager.clear();

        cleaner.cleanOrphans();

        assertThat(attachmentRepository.findById(attachment.getId()))
                .as("이력 보존을 위해 레코드는 물리 삭제하지 않는다")
                .isPresent();
        assertThatThrownBy(() -> localStorageService.read(attachment.getStorageKey()))
                .as("파일은 실제로 삭제되어야 한다")
                .isInstanceOf(java.io.UncheckedIOException.class);
    }

    @Test
    @DisplayName("소프트 삭제 7일이 지나지 않은 첨부는 파일이 남는다")
    void recentlySoftDeletedAttachmentKeepsFile() {
        User user = createUser("orphan-recent-deleted@example.com");
        Attachment attachment = createAttachmentWithFile(user);
        attachment.softDelete();
        attachmentRepository.save(attachment);
        backdateDeletedAt(attachment.getId(), Instant.now().minus(Duration.ofHours(1)));
        entityManager.flush();
        entityManager.clear();

        cleaner.cleanOrphans();

        assertThat(attachmentRepository.findById(attachment.getId())).isPresent();
        assertThat(localStorageService.read(attachment.getStorageKey())).isNotEmpty();
    }
}
