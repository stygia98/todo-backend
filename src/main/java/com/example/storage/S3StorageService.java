package com.example.storage;

import com.example.domain.Attachment;
import com.example.domain.StorageType;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

/**
 * AWS S3 기반 스토리지 구현체 (Phase 14).
 *
 * <p>{@code app.storage.type=s3}일 때만 등록된다. {@code LocalStorageService}는 이 빈의
 * 등록 여부와 무관하게 항상 등록되어 있으므로, {@link StorageServiceResolver}는 두 구현체를
 * 동시에 주입받아 {@code Attachment.storageType} 값으로 라우팅한다 — 이 클래스는 그 라우팅
 * 로직에 관여하지 않는다.
 *
 * <p>자격증명은 코드에 분기를 두지 않는다. {@link S3Client}·{@link S3Presigner}가 기본으로
 * 쓰는 {@code DefaultCredentialsProvider}가 로컬 개발 시 환경변수(AWS_ACCESS_KEY_ID 등)와
 * EC2 배포 시 IAM Role을 모두 자동으로 처리한다.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "s3")
public class S3StorageService implements StorageService {

    /**
     * presigned PUT은 발급 직후 바로 소비되므로 짧게 둔다. 조회용 presigned GET과 달리
     * "상세 화면을 열어둔 채 오래 유지"될 필요가 없다.
     */
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final Duration viewUrlExpiry;

    public S3StorageService(
            @Value("${app.storage.s3.bucket}") String bucket,
            @Value("${app.storage.s3.region}") String region,
            // 로컬 뷰 토큰과 같은 설정값을 공유한다 — 두 방식 모두 "상세 화면을 열어둔 채
            // 만료되지 않아야 한다"는 같은 이유(CLAUDE.md 6장 4-3절)로 24시간을 쓴다.
            @Value("${app.storage.url-expiry-millis}") long urlExpiryMillis) {
        this.bucket = bucket;
        Region awsRegion = Region.of(region);
        this.s3Client = S3Client.builder().region(awsRegion).build();
        this.s3Presigner = S3Presigner.builder().region(awsRegion).build();
        this.viewUrlExpiry = Duration.ofMillis(urlExpiryMillis);
    }

    @Override
    public StorageType getType() {
        return StorageType.S3;
    }

    @Override
    public String createUploadUrl(Attachment attachment) {
        // Content-Type을 서명에 포함한다. 프론트가 presign 응답의 contentType을 그대로
        // 업로드 헤더에 실어야 서명이 일치한다(appendFileImage.md 3절 — 다르면 403 SignatureDoesNotMatch).
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(attachment.getStorageKey())
                .contentType(attachment.getContentType())
                .build();
        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_EXPIRY)
                .putObjectRequest(putObjectRequest)
                .build();
        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    @Override
    public String createViewUrl(Attachment attachment) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(attachment.getStorageKey())
                .build();
        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(viewUrlExpiry)
                .getObjectRequest(getObjectRequest)
                .build();
        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    @Override
    public long verifyUploaded(String storageKey) {
        try {
            return s3Client.headObject(HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(storageKey)
                            .build())
                    .contentLength();
        } catch (NoSuchKeyException e) {
            throw new IllegalStateException("업로드된 파일을 찾을 수 없습니다: " + storageKey, e);
        }
    }

    @Override
    public byte[] readHeader(String storageKey, int maxBytes) {
        // 전체 다운로드 없이 Range로 앞부분만 가져온다. 실제 크기가 maxBytes보다 작으면
        // S3가 있는 만큼만(206 Partial Content) 돌려준다.
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .range("bytes=0-" + (maxBytes - 1))
                .build();
        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray();
    }

    @Override
    public void delete(String storageKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(storageKey)
                .build());
    }

    @PreDestroy
    void close() {
        s3Client.close();
        s3Presigner.close();
    }
}
