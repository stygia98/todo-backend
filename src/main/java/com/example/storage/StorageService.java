package com.example.storage;

import com.example.domain.Attachment;
import com.example.domain.StorageType;

/**
 * 로컬 파일시스템과 S3를 동일한 방식으로 다루기 위한 추상화.
 *
 * <p>구현체는 {@link StorageServiceResolver}가 {@link #getType()} 기준으로 색인한다.
 * {@code LocalStorageService}는 S3 전환 후에도 항상 등록되어야 한다 — {@code storage_type}
 * 컬럼을 둔 이유가 "과거 LOCAL 레코드도 계속 올바르게 조회하기 위함"이기 때문이다.
 */
public interface StorageService {

    StorageType getType();

    /** 업로드용 URL을 발급한다. S3는 presigned PUT, 로컬은 백엔드 업로드 엔드포인트 주소다. */
    String createUploadUrl(Attachment attachment);

    /** 조회용 URL을 발급한다. S3는 presigned GET, 로컬은 서명 토큰이 붙은 백엔드 조회 엔드포인트 주소다. */
    String createViewUrl(Attachment attachment);

    /**
     * 업로드 완료 후 실제 파일의 존재와 크기를 확인한다.
     *
     * @return 실측 바이트 수
     */
    long verifyUploaded(String storageKey);

    /**
     * 파일 앞부분 {@code maxBytes}만 읽는다. 매직바이트 검증처럼 전체 파일이 필요 없는
     * 경우 전용이다 — S3는 전체 다운로드 없이 Range GetObject로, 로컬은 스트림을
     * {@code maxBytes}만큼만 읽어 구현한다. 실제 파일이 더 작으면 남은 만큼만 반환한다.
     */
    byte[] readHeader(String storageKey, int maxBytes);

    void delete(String storageKey);
}
