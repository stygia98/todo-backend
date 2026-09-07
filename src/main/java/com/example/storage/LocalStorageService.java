package com.example.storage;

import com.example.config.AttachmentTokenProvider;
import com.example.domain.Attachment;
import com.example.domain.StorageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 로컬 파일시스템 기반 스토리지 구현체.
 *
 * <p>⚠️ <b>S3 전환 후에도 항상 {@code @Component}로 등록된 채로 남는다.</b> 조건부로 두면
 * 전환 후 과거 {@code storage_type=LOCAL} 레코드를 조회할 방법이 사라진다
 * ({@link StorageServiceResolver} 참조).
 *
 * <p>저장 키({@code storageKey})는 항상 {@code base-dir} 기준 상대경로로 다룬다.
 * 최종 경로는 {@link #resolveSafe(String)}에서 정규화 후 {@code base-dir} 하위인지
 * 검증하며, 벗어나면 예외를 던진다(경로 조작 방어).
 */
@Component
public class LocalStorageService implements StorageService {

    private final AttachmentTokenProvider tokenProvider;
    private final Path baseDir;
    private final String baseUrl;

    public LocalStorageService(
            AttachmentTokenProvider tokenProvider,
            // 기본값을 두지 않는다 — 미설정 시 기동 자체가 실패해야 한다(CLAUDE.md 12장의
            // DB_URL과 같은 원칙). 개발자가 로컬 업로드 디렉토리를 명시적으로 지정하게 만든다.
            @Value("${app.storage.local.base-dir}") String baseDir,
            @Value("${app.storage.local.base-url}") String baseUrl) {
        this.tokenProvider = tokenProvider;
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        this.baseUrl = baseUrl;
        try {
            Files.createDirectories(this.baseDir);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 디렉토리를 생성할 수 없습니다: " + this.baseDir, e);
        }
    }

    @Override
    public StorageType getType() {
        return StorageType.LOCAL;
    }

    @Override
    public String createUploadUrl(Attachment attachment) {
        return baseUrl + "/api/v1/attachments/" + attachment.getId() + "/upload";
    }

    /**
     * 조회 URL. S3의 presigned GET과 개념이 같도록, 서명된 뷰 토큰을 쿼리로 붙인
     * 백엔드 raw 엔드포인트 주소를 돌려준다.
     */
    @Override
    public String createViewUrl(Attachment attachment) {
        String token = tokenProvider.createViewToken(attachment.getId(), attachment.getUser().getId());
        return baseUrl + "/api/v1/attachments/" + attachment.getId() + "/raw?token=" + token;
    }

    @Override
    public long verifyUploaded(String storageKey) {
        Path path = resolveSafe(storageKey);
        if (!Files.exists(path)) {
            throw new IllegalStateException("업로드된 파일을 찾을 수 없습니다: " + storageKey);
        }
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void delete(String storageKey) {
        Path path = resolveSafe(storageKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 파일 수신. {@code StorageService} 공통 인터페이스에는 없다 — S3는 브라우저가
     * presigned URL로 직접 업로드해 백엔드를 거치지 않으므로, 이 메서드는 로컬 전용
     * {@code PUT /api/v1/attachments/{id}/upload} 엔드포인트에서만 쓰인다.
     */
    public void write(String storageKey, InputStream content) {
        Path path = resolveSafe(storageKey);
        try {
            Files.createDirectories(path.getParent());
            Files.copy(content, path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("파일 저장에 실패했습니다: " + storageKey, e);
        }
    }

    /**
     * 파일 전체를 읽는다. {@code StorageService} 공통 인터페이스에는 없다 — S3는 presigned GET을
     * 발급할 뿐 백엔드가 바이트를 직접 읽지 않으므로, 이 메서드는 로컬 전용 경로
     * ({@code /raw} 서빙, complete 단계의 매직바이트 검증)에서만 쓰인다.
     */
    public byte[] read(String storageKey) {
        Path path = resolveSafe(storageKey);
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException("파일을 읽을 수 없습니다: " + storageKey, e);
        }
    }

    /**
     * {@code storageKey}를 {@code base-dir} 기준 절대경로로 정규화하고, 그 결과가
     * {@code base-dir} 하위를 벗어나지 않는지 확인한다.
     *
     * <p>{@code storageKey}에 {@code ../}가 섞여 상위 디렉토리로 탈출을 시도해도,
     * {@link Path#normalize()}가 {@code ..}를 해소한 뒤의 최종 경로가 여기서 걸러진다.
     */
    private Path resolveSafe(String storageKey) {
        Path resolved = baseDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(baseDir)) {
            throw new IllegalArgumentException("허용된 업로드 디렉토리를 벗어난 경로입니다: " + storageKey);
        }
        return resolved;
    }
}
