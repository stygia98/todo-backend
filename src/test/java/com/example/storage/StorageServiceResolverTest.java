package com.example.storage;

import com.example.domain.Attachment;
import com.example.domain.StorageType;
import com.example.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link StorageServiceResolver} 단위 테스트.
 *
 * <p>실제 {@code S3StorageService}는 Phase 14에서 만들어지므로, 여기서는 두 번째 스토리지
 * 타입 역할을 하는 최소 가짜 구현체로 "여러 구현체가 공존해도 타입별로 올바르게 라우팅되는지"를
 * 검증한다 — Phase 14에서 실제 S3 구현체가 추가돼도 이 리졸버 자체는 수정할 필요가 없어야
 * 한다는 설계를 뒷받침하는 테스트다.
 */
class StorageServiceResolverTest {

    private static class FakeStorageService implements StorageService {
        private final StorageType type;

        FakeStorageService(StorageType type) {
            this.type = type;
        }

        @Override
        public StorageType getType() {
            return type;
        }

        @Override
        public String createUploadUrl(Attachment attachment) {
            return "upload-url-" + type;
        }

        @Override
        public String createViewUrl(Attachment attachment) {
            return "view-url-" + type;
        }

        @Override
        public long verifyUploaded(String storageKey) {
            return 0;
        }

        @Override
        public void delete(String storageKey) {
            // 검증 대상이 아니다.
        }
    }

    @Test
    @DisplayName("forNewUpload 은 app.storage.type 설정값에 해당하는 구현체를 반환한다")
    void forNewUploadReturnsConfiguredType() {
        FakeStorageService local = new FakeStorageService(StorageType.LOCAL);
        FakeStorageService s3 = new FakeStorageService(StorageType.S3);
        StorageServiceResolver resolver = new StorageServiceResolver(List.of(local, s3), "local");
        resolver.index();

        assertThat(resolver.forNewUpload()).isSameAs(local);
    }

    @Test
    @DisplayName("설정값이 대문자 enum과 달라도(local vs LOCAL) 매칭된다")
    void configuredTypeIsCaseInsensitive() {
        FakeStorageService s3 = new FakeStorageService(StorageType.S3);
        StorageServiceResolver resolver = new StorageServiceResolver(List.of(s3), "s3");
        resolver.index();

        assertThat(resolver.forNewUpload()).isSameAs(s3);
    }

    @Test
    @DisplayName("forAttachment 은 신규 업로드 설정과 무관하게 레코드 자신의 storage_type 을 따른다")
    void forAttachmentFollowsRecordOwnStorageType() {
        FakeStorageService local = new FakeStorageService(StorageType.LOCAL);
        FakeStorageService s3 = new FakeStorageService(StorageType.S3);
        // 신규 업로드 기본값은 S3 로 전환됐다고 가정한다(Phase 14 이후 상황을 흉내낸다).
        StorageServiceResolver resolver = new StorageServiceResolver(List.of(local, s3), "s3");
        resolver.index();

        User user = User.createLocal("owner@example.com", "{bcrypt}hash", "테스터");
        Attachment legacyLocalAttachment = Attachment.createTemp(
                user, StorageType.LOCAL, "todos/1/2026/09/a.png", "a.png", "image/png", 1L);

        // 신규 업로드는 S3 로 가지만, 과거 LOCAL 레코드는 여전히 LocalStorageService 로 라우팅된다.
        assertThat(resolver.forNewUpload()).isSameAs(s3);
        assertThat(resolver.forAttachment(legacyLocalAttachment)).isSameAs(local);
    }
}
