package com.example.storage;

import com.example.config.AttachmentTokenProvider;
import com.example.domain.Attachment;
import com.example.domain.StorageType;
import com.example.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link LocalStorageService} 단위 테스트.
 *
 * <p>실제 업로드 디렉토리를 오염시키지 않도록 {@link TempDir}로 임시 디렉토리를 주입한다.
 * Spring 컨텍스트를 띄우지 않고 생성자를 직접 호출한다 — 이 클래스의 책임은 순수 파일 IO와
 * 경로 검증이라 컨텍스트가 필요 없다.
 */
class LocalStorageServiceTest {

    // 실제 JWT 서명이 필요하지 않은 경로만 검증하므로 임의의 32바이트 이상 문자열이면 충분하다.
    private static final String TEST_SECRET = "test-only-signing-key-32bytes-minimum-000000";

    private LocalStorageService newService(Path baseDir) {
        AttachmentTokenProvider tokenProvider = new AttachmentTokenProvider(TEST_SECRET, 86_400_000L);
        return new LocalStorageService(tokenProvider, baseDir.toString(), "http://localhost:8080");
    }

    private Attachment newAttachment(Long id, User user) {
        Attachment attachment = Attachment.createTemp(
                user, StorageType.LOCAL, "todos/1/2026/09/photo.png", "photo.png", "image/png", 10L);
        setId(attachment, id);
        return attachment;
    }

    /** 테스트 전용 id 주입. 실제 저장 없이 {@code @Id} 값을 채워야 URL 조립을 검증할 수 있다. */
    private void setId(Object entity, Long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("기동 시 base-dir 이 없으면 자동 생성한다")
    void createsBaseDirIfMissing(@TempDir Path tempDir) {
        Path baseDir = tempDir.resolve("uploads-not-yet-created");

        newService(baseDir);

        assertThat(Files.isDirectory(baseDir)).isTrue();
    }

    @Test
    @DisplayName("write 로 저장한 파일을 verifyUploaded 로 실측 크기까지 읽어온다 (왕복)")
    void writeThenVerifyUploadedRoundTrips(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);
        byte[] content = "fake-image-bytes".getBytes();

        service.write("todos/1/2026/09/a.png", new ByteArrayInputStream(content));
        long size = service.verifyUploaded("todos/1/2026/09/a.png");

        assertThat(size).isEqualTo(content.length);
    }

    @Test
    @DisplayName("delete 이후에는 verifyUploaded 가 실패한다")
    void deleteRemovesFile(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);
        service.write("todos/1/2026/09/b.png", new ByteArrayInputStream("x".getBytes()));

        service.delete("todos/1/2026/09/b.png");

        assertThatThrownBy(() -> service.verifyUploaded("todos/1/2026/09/b.png"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("업로드되지 않은 키를 조회하면 예외가 난다")
    void verifyUploadedFailsWhenFileMissing(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);

        assertThatThrownBy(() -> service.verifyUploaded("todos/1/2026/09/never-uploaded.png"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("storageKey 에 ../ 가 섞여 base-dir 을 벗어나면 차단한다")
    void pathTraversalIsBlocked(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);

        assertThatThrownBy(() -> service.write("../../etc/passwd", new ByteArrayInputStream("x".getBytes())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verifyUploaded("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.delete("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createUploadUrl 은 로컬 업로드 엔드포인트 주소를 만든다")
    void createUploadUrlPointsToLocalEndpoint(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);
        User user = User.createLocal("owner@example.com", "{bcrypt}hash", "테스터");
        Attachment attachment = newAttachment(42L, user);

        String url = service.createUploadUrl(attachment);

        assertThat(url).isEqualTo("http://localhost:8080/api/v1/attachments/42/upload");
    }

    @Test
    @DisplayName("createViewUrl 은 서명된 뷰 토큰을 쿼리로 붙인다")
    void createViewUrlIncludesSignedToken(@TempDir Path tempDir) {
        LocalStorageService service = newService(tempDir);
        User user = User.createLocal("owner@example.com", "{bcrypt}hash", "테스터");
        setId(user, 7L);
        Attachment attachment = newAttachment(42L, user);

        String url = service.createViewUrl(attachment);

        assertThat(url).startsWith("http://localhost:8080/api/v1/attachments/42/raw?token=");
        // JWT 는 점 2개로 구분된 3개 세그먼트(header.payload.signature)다.
        String token = url.substring(url.indexOf("token=") + "token=".length());
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    @DisplayName("getType 은 LOCAL 을 반환한다")
    void typeIsLocal(@TempDir Path tempDir) {
        assertThat(newService(tempDir).getType()).isEqualTo(StorageType.LOCAL);
    }
}
