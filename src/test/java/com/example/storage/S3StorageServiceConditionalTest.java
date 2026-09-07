package com.example.storage;

import com.example.domain.StorageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link S3StorageService}의 조건부 빈 등록을 검증한다.
 *
 * <p>실제 AWS 네트워크 호출 없이도 {@link software.amazon.awssdk.services.s3.S3Client}·
 * {@link software.amazon.awssdk.services.s3.presigner.S3Presigner}의 자격증명 해석은
 * 지연 평가되므로, 빈 생성 자체는 로컬에서 안전하게 검증할 수 있다.
 */
class S3StorageServiceConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(S3StorageService.class)
            .withPropertyValues(
                    "app.storage.s3.bucket=test-bucket",
                    "app.storage.s3.region=ap-northeast-2",
                    "app.storage.url-expiry-millis=86400000");

    @Test
    @DisplayName("app.storage.type=local 이면 S3StorageService 빈이 생성되지 않는다")
    void beanNotCreatedWhenTypeIsLocal() {
        contextRunner.withPropertyValues("app.storage.type=local")
                .run(context -> assertThat(context).doesNotHaveBean(S3StorageService.class));
    }

    @Test
    @DisplayName("app.storage.type 이 미설정이어도 S3StorageService 빈이 생성되지 않는다")
    void beanNotCreatedWhenTypeIsMissing() {
        contextRunner.run(context -> assertThat(context).doesNotHaveBean(S3StorageService.class));
    }

    @Test
    @DisplayName("app.storage.type=s3 이면 S3StorageService 빈이 생성되고 getType()이 S3를 반환한다")
    void beanCreatedWhenTypeIsS3() {
        contextRunner.withPropertyValues("app.storage.type=s3")
                .run(context -> {
                    assertThat(context).hasSingleBean(S3StorageService.class);
                    assertThat(context.getBean(S3StorageService.class).getType())
                            .isEqualTo(StorageType.S3);
                });
    }
}
