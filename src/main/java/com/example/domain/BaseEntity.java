package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * 모든 엔티티가 상속하는 감사(auditing) 필드.
 *
 * <p>{@code created_at} 과 {@code updated_at} 을 JPA Auditing 으로 자동 관리한다.
 * 동작하려면 {@code @EnableJpaAuditing} 이 필요하며, 그 애노테이션은
 * <b>메인 애플리케이션 클래스에 붙어 있다.</b> {@code @Configuration} 클래스로 옮기면
 * {@code @DataJpaTest} 가 로드하지 않아 {@code createdAt} 이 null 이 된다.
 *
 * <h2>왜 {@code LocalDateTime} 이 아니라 {@code Instant} 인가</h2>
 *
 * <p><b>1. UTC 저장이 설정만으로는 보장되지 않는다.</b>
 * {@code hibernate.jdbc.time_zone: UTC} 는 JDBC 바인딩 계층에서 동작하는 설정이다.
 * 그런데 {@code @CreatedDate} 값을 실제로 만드는 주체는 Spring Data 의 시각 공급자이고,
 * 기본 구현은 <b>시스템 기본 타임존</b>(이 프로젝트의 개발 환경은 KST +09:00)을 쓴다.
 * {@code LocalDateTime} 은 타임존 정보가 없으므로, 감사 단계에서 만들어진 KST 벽시계 값이
 * 이후 UTC 로 재해석될 근거가 없다. 결국 KST 시각이 그대로 저장되어 운영(UTC)과 9시간 어긋난다.
 * {@code Instant} 는 정의상 UTC 기준 시점이라 공급자의 타임존과 무관하다.
 *
 * <p><b>2. API 응답 형식이 {@code Instant} 를 요구한다.</b>
 * CLAUDE.md 5장은 {@code createdAt} 을 {@code 2026-08-28T04:30:00Z} 형태로 직렬화하도록 규정한다.
 * 끝의 {@code Z} 는 UTC 를 뜻하는데, {@code LocalDateTime} 은 타임존이 없어 이 접미사를 붙일 수 없다.
 *
 * <p>{@code Instant} 는 Hibernate 에서 {@code TIMESTAMP} 에 매핑되므로 CLAUDE.md 4장 스키마와도 일치한다.
 * 반면 {@code due_date} 는 시각이 없어 타임존 영향을 받지 않으므로 {@code LocalDate} 를 그대로 쓴다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    /** 생성 시각(UTC). 최초 저장 이후 변경되지 않는다. */
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 최종 수정 시각(UTC). */
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
