package com.example.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 사용자. CLAUDE.md 4장 {@code users} 표와 1:1로 대응한다.
 *
 * <p>생성은 {@link #createLocal} 과 {@link #createGoogle} 두 정적 팩토리로만 한다.
 * 소셜 전용 계정은 비밀번호가 없다는 사실이 메서드 시그니처에 드러나므로,
 * 생성자 하나에 null 을 넘기는 방식보다 실수를 줄인다.
 *
 * <p>{@code @Setter} 를 두지 않는다(CLAUDE.md 10장). 변경이 필요하면 의미 있는 메서드를 추가한다.
 */
@Entity
@Getter
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 ID. 중복될 수 없다. */
    @Column(name = "email", length = 255, nullable = false, unique = true)
    private String email;

    /**
     * BCrypt 해시. 소셜 전용 계정은 null 이다.
     *
     * <p>평문 길이 제한은 6자 이상 + UTF-8 72바이트 이하이며 DTO 검증 단계에서 처리한다.
     * BCrypt 한계가 72바이트라 한글 25자면 이미 초과한다(CLAUDE.md 4장).
     */
    @Column(name = "password", length = 255)
    private String password;

    @Column(name = "nickname", length = 50, nullable = false)
    private String nickname;

    /** ORDINAL 로 두면 enum 선언 순서 변경 시 기존 데이터의 의미가 바뀐다. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20, nullable = false)
    private AuthProvider provider;

    /** 소셜 로그인 제공자가 발급한 고유 ID. LOCAL 계정은 null 이다. */
    @Column(name = "provider_id", length = 255)
    private String providerId;

    /**
     * 소프트 삭제 시각(UTC).
     *
     * <p>회원 탈퇴가 이번 범위의 비목표라 <b>이 값을 채우는 경로가 아직 없다.</b>
     * 다만 스키마와 조회 조건은 유지해, 향후 탈퇴 기능을 추가할 때 구조를 바꾸지 않게 한다.
     * 인증 시 {@code deleted_at IS NULL} 검사는 지금도 걸어둔다.
     */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    private User(String email, String password, String nickname,
                 AuthProvider provider, String providerId) {
        this.email = email;
        this.password = password;
        this.nickname = nickname;
        this.provider = provider;
        this.providerId = providerId;
    }

    /** 이메일 가입. {@code encodedPassword} 는 반드시 BCrypt 해시여야 한다. */
    public static User createLocal(String email, String encodedPassword, String nickname) {
        return new User(email, encodedPassword, nickname, AuthProvider.LOCAL, null);
    }

    /**
     * 구글 가입. 비밀번호가 없다.
     *
     * <p>{@code nickname} 결정 규칙은 CLAUDE.md 6장에 있다.
     * 구글이 준 name → 없으면 이메일의 @ 앞부분 → 50자 초과 시 절삭.
     */
    public static User createGoogle(String email, String nickname, String providerId) {
        return new User(email, null, nickname, AuthProvider.GOOGLE, providerId);
    }

    /** 소프트 삭제. 물리 삭제를 하지 않는다. */
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
