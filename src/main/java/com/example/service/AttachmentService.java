package com.example.service;

import com.example.domain.Attachment;
import com.example.domain.AttachmentRepository;
import com.example.domain.AttachmentStatus;
import com.example.domain.Todo;
import com.example.domain.User;
import com.example.dto.AttachmentPresignRequest;
import com.example.dto.AttachmentPresignResponse;
import com.example.dto.AttachmentViewResponse;
import com.example.dto.TodoResponse;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import com.example.storage.LocalStorageService;
import com.example.storage.StorageService;
import com.example.storage.StorageServiceResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 첨부 이미지 업로드 검증, 상태 전환, Todo 연결을 담당한다.
 *
 * <p>소유권 검증({@link #findOwned})은 {@code TodoService}와 같은 정책이다 — 불일치 시
 * 403이 아니라 404(존재 여부 비노출).
 */
@Service
@Transactional(readOnly = true)
public class AttachmentService {

    private static final DateTimeFormatter KEY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM");
    /** 매직바이트 검증에 필요한 최대 길이. WebP 검증(RIFF....WEBP)이 12바이트로 가장 길다. */
    private static final int MAGIC_BYTE_HEADER_SIZE = 12;

    private final AttachmentRepository attachmentRepository;
    private final StorageServiceResolver storageServiceResolver;
    private final long maxFileSize;
    private final Set<String> allowedContentTypes;

    public AttachmentService(
            AttachmentRepository attachmentRepository,
            StorageServiceResolver storageServiceResolver,
            @Value("${app.upload.max-file-size}") long maxFileSize,
            @Value("${app.upload.allowed-content-types}") String allowedContentTypesCsv) {
        this.attachmentRepository = attachmentRepository;
        this.storageServiceResolver = storageServiceResolver;
        this.maxFileSize = maxFileSize;
        this.allowedContentTypes = Set.of(allowedContentTypesCsv.split(","));
    }

    /**
     * 업로드 URL 발급. presign 단계의 {@code fileSize}는 클라이언트 주장값이라 상한 검사에만
     * 쓰고, 실측값은 {@link #complete(Long, Long)}에서 갱신한다.
     */
    @Transactional
    public AttachmentPresignResponse presign(User user, AttachmentPresignRequest request) {
        if (!allowedContentTypes.contains(request.contentType())) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
        if (request.fileSize() > maxFileSize) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        StorageService storageService = storageServiceResolver.forNewUpload();
        String storageKey = buildStorageKey(user.getId(), request.contentType());
        Attachment attachment = Attachment.createTemp(
                user, storageService.getType(), storageKey,
                request.filename(), request.contentType(), request.fileSize());
        attachmentRepository.save(attachment);

        String uploadUrl = storageService.createUploadUrl(attachment);
        return new AttachmentPresignResponse(attachment.getId(), uploadUrl, storageKey, request.contentType());
    }

    /**
     * 로컬 전용 파일 수신. {@code PUT /api/v1/attachments/{id}/upload}에서만 쓰인다.
     *
     * <p>⚠️ S3 presigned PUT과 달리 이 경로는 백엔드가 직접 처리하므로 일반 API처럼
     * {@code Authorization} 헤더로 인증한다(S3는 서명된 URL 자체가 인가를 대신한다).
     */
    @Transactional
    public void upload(Long id, Long userId, InputStream content) {
        Attachment attachment = findOwned(id, userId);
        if (attachment.getStatus() != AttachmentStatus.TEMP) {
            throw new BusinessException(ErrorCode.ATTACHMENT_ALREADY_UPLOADED);
        }
        requireLocal(storageServiceResolver.forAttachment(attachment)).write(attachment.getStorageKey(), content);
    }

    /**
     * 업로드 완료 확정. 실제 파일 존재·크기·매직바이트를 확인한 뒤에만 {@code LINKED} 전
     * 단계인 실측 크기 갱신을 반영한다(연결 자체는 Todo 저장 시 {@link #link}가 한다).
     *
     * <p>검증에 실패하면 손상되었거나 위조된 파일이므로 즉시 삭제한다. 링크 해제(Todo 저장
     * 시 본문에서 빠짐)처럼 롤백 가능성이 있는 삭제가 아니라, 애초에 유효하지 않은 파일을
     * 치우는 것이므로 트랜잭션 안에서 바로 지운다.
     *
     * <p>존재·크기 확인({@code verifyUploaded})과 매직바이트용 헤더 읽기({@code readHeader})는
     * {@code StorageService} 공통 인터페이스로 처리해 LOCAL·S3 양쪽에서 동작한다 — S3는
     * Range GetObject로 앞부분만 가져오므로 전체 다운로드가 필요 없다.
     */
    @Transactional
    public AttachmentViewResponse complete(Long id, Long userId) {
        Attachment attachment = findOwned(id, userId);
        StorageService storageService = storageServiceResolver.forAttachment(attachment);

        long actualSize = storageService.verifyUploaded(attachment.getStorageKey());
        if (actualSize > maxFileSize) {
            storageService.delete(attachment.getStorageKey());
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] header = storageService.readHeader(attachment.getStorageKey(), MAGIC_BYTE_HEADER_SIZE);
        if (!hasValidMagicBytes(header, attachment.getContentType())) {
            storageService.delete(attachment.getStorageKey());
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        attachment.markUploaded(actualSize);
        return new AttachmentViewResponse(attachment.getId(), storageService.createViewUrl(attachment));
    }

    /** 조회용 URL 재발급. */
    public AttachmentViewResponse viewUrl(Long id, Long userId) {
        Attachment attachment = findOwned(id, userId);
        StorageService storageService = storageServiceResolver.forAttachment(attachment);
        return new AttachmentViewResponse(attachment.getId(), storageService.createViewUrl(attachment));
    }

    /** Soft Delete. 실제 파일 삭제는 고아 파일 정리 배치가 담당한다. */
    @Transactional
    public void delete(Long id, Long userId) {
        Attachment attachment = findOwned(id, userId);
        attachment.softDelete();
    }

    /**
     * Todo 저장 시 본문에 실제로 남아 있는 첨부만 연결한다.
     *
     * <p>⚠️ 호출부({@code TodoService})는 반드시 {@code htmlSanitizer.clean()} <b>이후</b>의
     * 정본 HTML에서 수집한 id 집합을 넘겨야 한다. 정화 전 원문에서 수집하면 제거될 {@code img}의
     * 첨부까지 승격되어 영원히 정리되지 않는 고아가 생긴다.
     *
     * <ul>
     *   <li>신규 id → {@link Attachment#linkTo(Todo)}로 전환
     *   <li>기존에 이 Todo에 연결돼 있었으나 이번 본문에 없는 id → {@link Attachment#softDelete()}
     *   <li>존재하지 않는 id·타인 소유 id·이미 다른 Todo에 LINKED된 id → <b>구분 없이</b> 400
     *       {@code INVALID_INPUT}. 세 경우를 구분하면 존재 여부·소유 여부가 노출된다.
     * </ul>
     */
    @Transactional
    public void link(Todo todo, Long userId, Set<Long> attachmentIds) {
        List<Attachment> currentlyLinked =
                attachmentRepository.findByTodoIdInAndDeletedAtIsNull(List.of(todo.getId()));
        Set<Long> currentlyLinkedIds = currentlyLinked.stream()
                .map(Attachment::getId)
                .collect(Collectors.toSet());

        Set<Long> newIds = new HashSet<>(attachmentIds);
        newIds.removeAll(currentlyLinkedIds);
        for (Long newId : newIds) {
            Attachment attachment = attachmentRepository.findByIdAndDeletedAtIsNull(newId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
            boolean ownedByCaller = attachment.getUser().getId().equals(userId);
            boolean alreadyLinkedElsewhere = attachment.getStatus() == AttachmentStatus.LINKED;
            if (!ownedByCaller || alreadyLinkedElsewhere) {
                throw new BusinessException(ErrorCode.INVALID_INPUT);
            }
            attachment.linkTo(todo);
        }

        Set<Long> removedIds = new HashSet<>(currentlyLinkedIds);
        removedIds.removeAll(attachmentIds);
        currentlyLinked.stream()
                .filter(attachment -> removedIds.contains(attachment.getId()))
                .forEach(Attachment::softDelete);
    }

    /**
     * 여러 Todo에 연결된 첨부의 조회용 URL을 한 번에 만든다.
     *
     * <p>{@code TodoService.list()}가 페이지 내 전체 todo id를 이 메서드로 배치 조회해
     * {@code TodoResponse.attachments}를 채운다 — 항목마다 개별 조회하면 N+1이 된다.
     * {@code a.getTodo().getId()}는 LAZY 프록시에서 식별자만 읽으므로 추가 쿼리를
     * 일으키지 않는다({@code Todo.user} 접근과 같은 원리).
     */
    public Map<Long, List<TodoResponse.AttachmentView>> viewsForTodos(List<Long> todoIds) {
        if (todoIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByTodoIdInAndDeletedAtIsNull(todoIds).stream()
                .collect(Collectors.groupingBy(
                        attachment -> attachment.getTodo().getId(),
                        Collectors.mapping(this::toAttachmentView, Collectors.toList())));
    }

    private TodoResponse.AttachmentView toAttachmentView(Attachment attachment) {
        StorageService storageService = storageServiceResolver.forAttachment(attachment);
        return new TodoResponse.AttachmentView(attachment.getId(), storageService.createViewUrl(attachment));
    }

    /**
     * 원본 파일 조회. 뷰 토큰 검증은 컨트롤러가 한다({@code /raw}는 이 프로젝트에서
     * 컨트롤러가 인가를 직접 수행하는 유일한 경로 — CLAUDE.md 6장).
     */
    public byte[] loadRaw(Attachment attachment) {
        return requireLocal(storageServiceResolver.forAttachment(attachment)).read(attachment.getStorageKey());
    }

    /** {@code /raw} 엔드포인트가 토큰 검증 이후 첨부 존재 여부를 확인할 때 쓴다. */
    public Attachment findExisting(Long id) {
        return attachmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
    }

    private Attachment findOwned(Long id, Long userId) {
        Attachment attachment = attachmentRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND));
        if (!attachment.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.ATTACHMENT_NOT_FOUND);
        }
        return attachment;
    }

    /**
     * S3는 presigned URL로 브라우저가 직접 업로드/조회하므로 백엔드가 바이트를 다루지 않는다.
     * 이 메서드들은 로컬 전용 경로에서만 호출되어야 하며, Phase 14에서 S3 전용 요청이 여기
     * 도달하면 추상화 설계 자체가 잘못된 것이므로 조용히 넘기지 않고 즉시 실패시킨다.
     */
    private LocalStorageService requireLocal(StorageService storageService) {
        if (storageService instanceof LocalStorageService local) {
            return local;
        }
        throw new UnsupportedOperationException(
                "이 작업은 로컬 스토리지 전용입니다: " + storageService.getType());
    }

    private String buildStorageKey(Long userId, String contentType) {
        String extension = extensionFor(contentType);
        String yearMonth = KEY_DATE_FORMAT.format(LocalDate.now());
        return "todos/%d/%s/%s.%s".formatted(userId, yearMonth, UUID.randomUUID(), extension);
    }

    /** 확장자는 파일명이 아니라 검증된 contentType에서 유도한다(이중 확장자·경로 조작 방지). */
    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        };
    }

    /**
     * 선두 바이트로 실제 형식을 확인한다. {@code contentType}은 클라이언트 주장값이므로
     * 화이트리스트 검증만으로는 부족하다 — 확장자만 바꾼 위조 파일을 걸러낸다.
     */
    private boolean hasValidMagicBytes(byte[] header, String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> startsWith(header, 0xFF, 0xD8, 0xFF);
            case "image/png" -> startsWith(header, 0x89, 0x50, 0x4E, 0x47);
            case "image/gif" -> startsWith(header, 0x47, 0x49, 0x46, 0x38);
            case "image/webp" -> header.length >= 12
                    && startsWith(header, 0x52, 0x49, 0x46, 0x46) // "RIFF"
                    && header[8] == 'W' && header[9] == 'E' && header[10] == 'B' && header[11] == 'P';
            default -> false;
        };
    }

    private boolean startsWith(byte[] data, int... expectedUnsignedBytes) {
        if (data.length < expectedUnsignedBytes.length) {
            return false;
        }
        for (int i = 0; i < expectedUnsignedBytes.length; i++) {
            if ((data[i] & 0xFF) != expectedUnsignedBytes[i]) {
                return false;
            }
        }
        return true;
    }
}
