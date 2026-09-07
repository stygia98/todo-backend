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

/**
 * 첨부 이미지. CLAUDE.md 4장 {@code attachments} 표와 1:1로 대응한다.
 *
 * <p>인덱스는 두 개다. {@code (todo_id, deleted_at)}은 Todo 단건/목록 조회 시
 * {@code TodoService}가 배치 조회하는 주 경로를 덮고, {@code (status, created_at)}은
 * 고아 파일 정리 배치(TEMP 24시간 경과 탐색)를 위한 것이다.
 *
 * <p>{@code @Setter}를 두지 않는다. 상태 변경은 {@link #linkTo(Todo)},
 * {@link #markUploaded(long)}, {@link #softDelete()} 세 메서드로만 한다.
 */
@Entity
@Getter
@Table(
        name = "attachments",
        indexes = {
                @Index(name = "idx_attachments_todo_deleted", columnList = "todo_id, deleted_at"),
                @Index(name = "idx_attachments_status_created", columnList = "status, created_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Attachment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 연결된 할 일. 업로드 시점(status=TEMP)에는 null이고, 할 일 저장/수정 시
     * 본문에 실제로 남아 있는 첨부만 {@link #linkTo(Todo)}로 채워진다.
     *
     * <p>⚠️ {@code @ManyToOne}의 기본값은 EAGER다. 반드시 LAZY를 지정한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "todo_id")
    private Todo todo;

    /**
     * 업로더. 소유권 검증(첨부 조회·연결 시 {@code attachment.getUser().getId()} 비교)에 쓰인다.
     * Todo 저장 전에 업로드가 먼저 일어나므로 {@code todo}와 별도로 반드시 채운다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_type", length = 20, nullable = false)
    private StorageType storageType;

    /** 로컬 상대경로 또는 S3 객체 키. 저장 키 규칙은 {@code StorageService} 구현체가 결정한다. */
    @Column(name = "storage_key", length = 512, nullable = false, unique = true)
    private String storageKey;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    /** 검증된 값만 저장한다(클라이언트가 보낸 파일명·헤더를 그대로 믿지 않는다). */
    @Column(name = "content_type", length = 100, nullable = false)
    private String contentType;

    /** bytes. presign 시점은 클라이언트 주장값이며, {@link #markUploaded(long)}으로 실측값으로 갱신된다. */
    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private AttachmentStatus status;

    /** 소프트 삭제 시각(UTC). 물리 삭제를 하지 않는다. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private Attachment(User user, StorageType storageType, String storageKey,
                        String originalFilename, String contentType, long fileSize) {
        this.user = user;
        this.storageType = storageType;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.status = AttachmentStatus.TEMP;
    }

    /** presign 단계에서 호출한다. {@code todo}는 아직 없다(status=TEMP). */
    public static Attachment createTemp(User user, StorageType storageType, String storageKey,
                                         String originalFilename, String contentType, long fileSize) {
        return new Attachment(user, storageType, storageKey, originalFilename, contentType, fileSize);
    }

    /**
     * Todo 본문에 실제로 남아 있는 첨부를 연결한다.
     *
     * <p>호출 시점은 {@code htmlSanitizer.clean()} 이후의 정본 HTML에서 id를 수집한 뒤여야 한다.
     * 정화 전 원문에서 수집하면 제거된 {@code img}의 첨부까지 승격되어 영원히 정리되지 않는
     * 고아가 생긴다.
     */
    public void linkTo(Todo todo) {
        this.todo = todo;
        this.status = AttachmentStatus.LINKED;
    }

    /** complete 단계에서 실제 파일 크기로 갱신한다. presign 시점의 값은 클라이언트 주장값일 뿐이다. */
    public void markUploaded(long actualSize) {
        this.fileSize = actualSize;
    }

    /** 소프트 삭제. 실제 파일 삭제는 고아 파일 정리 배치가 담당한다(트랜잭션 안에서 지우지 않는다). */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
