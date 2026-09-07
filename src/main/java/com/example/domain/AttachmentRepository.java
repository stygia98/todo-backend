package com.example.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 첨부 파일 조회.
 *
 * <p>⚠️ <b>모든 조회 메서드에 {@code AndDeletedAtIsNull}을 붙인다.</b> 빠뜨리면 소프트
 * 삭제한 첨부가 조회에 다시 나타난다.
 */
public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    /**
     * 단건 조회.
     *
     * <p>소유권 검증은 서비스 계층에서 {@code attachment.getUser().getId()}와 인증 사용자 id를
     * 비교해 한다. 불일치 시 403이 아니라 <b>404</b>를 반환한다(존재 여부 노출 방지).
     */
    Optional<Attachment> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 여러 Todo에 연결된 첨부를 한 번에 조회한다.
     *
     * <p>{@code TodoService.list()}가 페이지 내 전체 todo id를 이 메서드로 배치 조회해
     * {@code TodoResponse.attachments}를 채운다. 항목마다 개별 조회하면 N+1이 된다.
     */
    List<Attachment> findByTodoIdInAndDeletedAtIsNull(List<Long> todoIds);

    /**
     * 고아 파일 정리 배치 전용. {@code status=TEMP}인 채로 {@code threshold}보다 먼저
     * 생성된 레코드 — Todo 저장으로 이어지지 못하고 방치된 업로드다.
     * 인덱스 {@code idx_attachments_status_created}({@code status, created_at})가 덮는다.
     */
    List<Attachment> findByStatusAndCreatedAtBefore(AttachmentStatus status, Instant threshold);

    /**
     * 고아 파일 정리 배치 전용. {@code deleted_at}이 {@code threshold}보다 이전인 레코드.
     * SQL에서 {@code NULL < threshold}는 참이 되지 않으므로, 소프트 삭제되지 않은 행은
     * {@code IsNotNull} 조건 없이도 자연히 제외된다.
     */
    List<Attachment> findByDeletedAtBefore(Instant threshold);
}
