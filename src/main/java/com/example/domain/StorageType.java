package com.example.domain;

/**
 * 첨부 파일의 실제 저장 위치.
 *
 * <p>DB에는 {@code VARCHAR(20)}으로 저장된다. 반드시 {@code @Enumerated(EnumType.STRING)}으로 매핑한다.
 *
 * <p>이 컬럼을 두는 이유는 로컬에서 만든 데이터와 S3 전환 이후 데이터가 섞여도 각각 올바른
 * 방식으로 조회하기 위함이다. {@code LocalStorageService}는 S3 전환 후에도 항상 등록해
 * 과거 {@code LOCAL} 레코드를 계속 조회할 수 있게 한다({@code StorageServiceResolver} 참조).
 */
public enum StorageType {

    /** 로컬 파일시스템(Phase 12~13). */
    LOCAL,

    /** AWS S3(Phase 14부터). */
    S3
}
