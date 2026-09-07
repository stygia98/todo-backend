package com.example.service;

import com.example.domain.Attachment;
import com.example.domain.AttachmentRepository;
import com.example.domain.AttachmentStatus;
import com.example.storage.StorageServiceResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 고아 첨부 파일을 정리하는 배치. 하루 1회 실행된다.
 *
 * <p>⚠️ <b>EC2 단일 인스턴스를 전제한다.</b> 인스턴스를 다중화하면 배치가 중복 실행된다
 * (분산 락 없음). 오류는 아니다 — {@code StorageService.delete}는 대상이 이미 없어도
 * 조용히 넘어가는 멱등 연산이라 중복 실행 자체가 잘못된 결과를 만들지는 않지만, 자원 낭비다.
 * 다중화 시점에는 분산 락이나 별도 배치 전용 인스턴스 분리를 검토한다.
 *
 * <h2>왜 여기서는 트랜잭션 안에서 파일을 지워도 되는가</h2>
 *
 * <p>{@code AttachmentService.link()}·{@code TodoService}는 <b>실제 파일 삭제를 이 배치에
 * 위임</b>하고 트랜잭션 안에서는 {@code softDelete()}만 한다(롤백 가능성이 있는 트랜잭션에서
 * 되돌릴 수 없는 파일 삭제를 미리 해버리면 안 되기 때문). 이 배치가 바로 그 "위임받은 실제
 * 삭제 주체"이므로, 여기서 파일을 지우는 것은 원칙 위반이 아니라 원칙이 가리키는 지점이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanAttachmentCleaner {

    /** TEMP 상태로 이 시간을 넘기면 Todo 저장으로 이어지지 못하고 방치된 것으로 본다. */
    private static final Duration TEMP_EXPIRY = Duration.ofHours(24);

    /** 소프트 삭제 후 이 기간이 지나면 실제 파일을 지운다. 레코드는 남긴다(이력 보존). */
    private static final Duration SOFT_DELETE_FILE_RETENTION = Duration.ofDays(7);

    private final AttachmentRepository attachmentRepository;
    private final StorageServiceResolver storageServiceResolver;

    /** 매일 새벽 4시(서버 로컬 시각). 트래픽이 가장 적은 시간대를 고른다. */
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void cleanOrphans() {
        int expiredTempCount = cleanExpiredTempAttachments();
        int deletedFileCount = cleanFilesForOldSoftDeletedAttachments();
        log.info("고아 첨부 정리 완료: TEMP 만료 {}건 삭제, 소프트 삭제 파일 {}건 정리",
                expiredTempCount, deletedFileCount);
    }

    /** {@code status=TEMP}이고 24시간이 지난 레코드를 실제 파일과 함께 삭제한다. */
    private int cleanExpiredTempAttachments() {
        List<Attachment> expired = attachmentRepository.findByStatusAndCreatedAtBefore(
                AttachmentStatus.TEMP, Instant.now().minus(TEMP_EXPIRY));

        for (Attachment attachment : expired) {
            deleteFileQuietly(attachment);
        }
        // TEMP 는 애초에 Todo 에 연결된 적이 없어 이력으로서 가치가 없으므로 레코드도 물리 삭제한다.
        // (LINKED 였다가 softDelete 된 레코드와의 차이 — 그쪽은 이력 보존을 위해 레코드를 남긴다.)
        attachmentRepository.deleteAll(expired);
        return expired.size();
    }

    /** {@code deleted_at}이 7일 지난 레코드의 실제 파일만 지운다. 레코드는 유지한다. */
    private int cleanFilesForOldSoftDeletedAttachments() {
        List<Attachment> oldDeleted = attachmentRepository.findByDeletedAtBefore(
                Instant.now().minus(SOFT_DELETE_FILE_RETENTION));

        for (Attachment attachment : oldDeleted) {
            deleteFileQuietly(attachment);
        }
        return oldDeleted.size();
    }

    /**
     * 파일 삭제 중 오류가 나도 배치 전체를 중단하지 않는다. 한 파일의 문제(권한, 이미 없음 등)로
     * 나머지 대상까지 처리되지 않으면 고아가 계속 쌓인다.
     */
    private void deleteFileQuietly(Attachment attachment) {
        try {
            storageServiceResolver.forAttachment(attachment).delete(attachment.getStorageKey());
        } catch (RuntimeException e) {
            log.warn("첨부 파일 삭제에 실패했습니다. id={}, storageKey={}",
                    attachment.getId(), attachment.getStorageKey(), e);
        }
    }
}
