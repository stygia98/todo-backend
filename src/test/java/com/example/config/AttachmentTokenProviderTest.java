package com.example.config;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AttachmentTokenProvider} 단위 테스트.
 *
 * <p>양방향 권한 상승 방지 중 "뷰 토큰 판별" 쪽을 담당한다. 반대 방향(액세스 토큰이
 * {@code purpose=="access"} 검사를 통과하는지)은 {@link JwtAuthenticationFilter}가 담당하며,
 * 그 필터 동작은 {@code AttachmentViewTokenFilterTest}(통합 테스트)에서 검증한다.
 */
class AttachmentTokenProviderTest {

    private static final String SECRET = "test-only-signing-key-32bytes-minimum-000000";

    private AttachmentTokenProvider newProvider(long expirationMillis) {
        return new AttachmentTokenProvider(SECRET, expirationMillis);
    }

    @Test
    @DisplayName("발급한 토큰은 purpose=attachment-view, aid=첨부id 클레임을 담는다")
    void createViewTokenCarriesExpectedClaims() {
        AttachmentTokenProvider provider = newProvider(86_400_000L);

        String token = provider.createViewToken(42L, 7L);
        Claims claims = provider.parseViewClaims(token).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("purpose", String.class)).isEqualTo("attachment-view");
        assertThat(claims.get("aid", Long.class)).isEqualTo(42L);
    }

    @Test
    @DisplayName("isValidViewToken 은 purpose 와 aid 가 모두 일치할 때만 true 다")
    void isValidViewTokenRequiresPurposeAndMatchingAid() {
        AttachmentTokenProvider provider = newProvider(86_400_000L);
        Claims claims = provider.parseViewClaims(provider.createViewToken(42L, 7L)).orElseThrow();

        assertThat(provider.isValidViewToken(claims, 42L)).isTrue();
    }

    /** 다른 첨부의 aid 를 가진 토큰으로 접근하는 경우 — 양방향 권한 상승 방지 중 두 번째. */
    @Test
    @DisplayName("다른 첨부의 aid 를 가진 토큰은 거부된다")
    void isValidViewTokenRejectsMismatchedAid() {
        AttachmentTokenProvider provider = newProvider(86_400_000L);
        Claims claims = provider.parseViewClaims(provider.createViewToken(42L, 7L)).orElseThrow();

        assertThat(provider.isValidViewToken(claims, 999L)).isFalse();
    }

    /**
     * 액세스 토큰(purpose=access)이 같은 시크릿으로 서명돼 있어 파싱 자체는 성공하더라도,
     * purpose 가 attachment-view 가 아니므로 거부되어야 한다.
     */
    @Test
    @DisplayName("purpose 가 access 인 토큰은 거부된다")
    void isValidViewTokenRejectsAccessPurpose() {
        JwtTokenProvider accessTokenProvider = new JwtTokenProvider(SECRET, 86_400_000L);
        AttachmentTokenProvider viewTokenProvider = newProvider(86_400_000L);

        String accessToken = accessTokenProvider.createToken(7L, "owner@example.com");
        Claims claims = viewTokenProvider.parseViewClaims(accessToken).orElseThrow();

        assertThat(claims.get("purpose", String.class)).isEqualTo("access");
        assertThat(viewTokenProvider.isValidViewToken(claims, 42L)).isFalse();
    }

    @Test
    @DisplayName("만료된 뷰 토큰은 파싱 자체가 빈 값이 된다")
    void expiredViewTokenParsesToEmpty() {
        // 발급 즉시 만료되도록 음수 만료시간을 준다.
        AttachmentTokenProvider provider = newProvider(-1_000L);

        String expiredToken = provider.createViewToken(42L, 7L);

        assertThat(provider.parseViewClaims(expiredToken)).isEmpty();
    }
}
