package com.example.storage;

import com.example.domain.Attachment;
import com.example.domain.StorageType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 등록된 {@link StorageService} 구현체를 {@link StorageType}별로 색인해 조회한다.
 *
 * <p>⚠️ {@code @ConditionalOnProperty}로 구현체를 하나만 등록하지 않는다. {@code LocalStorageService}는
 * 항상 등록되고, {@code S3StorageService}(Phase 14)만 조건부로 등록되어 이 클래스에 함께 주입된다.
 * 이 리졸버는 어떤 구현체가 존재하는지 알 필요가 없다 — {@code List<StorageService>}를 통째로
 * 받아 색인만 한다. Phase 14에서 S3 구현체가 추가돼도 <b>이 클래스는 수정할 필요가 없어야 한다.</b>
 */
@Service
public class StorageServiceResolver {

    private final List<StorageService> services;
    private final StorageType defaultType;
    private final Map<StorageType, StorageService> byType = new EnumMap<>(StorageType.class);

    public StorageServiceResolver(
            List<StorageService> services,
            @Value("${app.storage.type}") String defaultType) {
        this.services = services;
        // 설정값은 @ConditionalOnProperty(havingValue = "s3")와 형식을 맞추기 위해 소문자로 쓴다
        // (application-local.yml: local / application-prod.yml: s3, Phase 14). enum 상수는
        // 대문자이므로 여기서만 변환한다.
        this.defaultType = StorageType.valueOf(defaultType.toUpperCase(Locale.ROOT));
    }

    @PostConstruct
    void index() {
        for (StorageService service : services) {
            byType.put(service.getType(), service);
        }
    }

    /** 새로 업로드할 때 쓸 기본 스토리지. {@code app.storage.type} 설정값을 따른다. */
    public StorageService forNewUpload() {
        return byType.get(defaultType);
    }

    /** 기존 레코드를 다룰 때는 그 레코드가 저장된 방식을 따른다. */
    public StorageService forAttachment(Attachment attachment) {
        return byType.get(attachment.getStorageType());
    }
}
