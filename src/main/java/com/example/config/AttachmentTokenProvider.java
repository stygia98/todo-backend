package com.example.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * 첨부 이미지 조회용 서명 토큰 발급.
 *
 * <p>브라우저 {@code <img>} 태그는 {@code Authorization} 헤더를 실을 수 없으므로,
 * 조회 URL에 단기 서명 토큰을 쿼리로 붙인다. {@link JwtTokenProvider}와 같은
 * {@code jwt.secret}/HS256을 재사용하되, 발급 목적이 달라 클래스는 분리한다.
 *
 * <h2>클레임 구성</h2>
 * <pre>
 * sub     : user.id (업로더, 숫자 문자열)
 * purpose : "attachment-view"   ← 액세스 토큰(access)과 반드시 구분된다
 * aid     : 첨부 id — 이 토큰은 해당 첨부 1건에만 유효하다
 * iat/exp : 발급 시각 / 발급 + 만료시간
 * </pre>
 *
 * <p>⚠️ 같은 키로 서명하므로 서명 검증만으로는 이 토큰과 액세스 토큰이 구분되지 않는다.
 * {@code JwtAuthenticationFilter}가 {@code purpose=="access"}인 토큰만 인증에 사용하도록
 * 막는 작업(양방향 권한 상승 방지)은 별도 태스크에서 {@code JwtAuthenticationFilter}와
 * 첨부 조회 컨트롤러에 함께 들어간다. 이 클래스는 발급만 담당한다.
 *
 * <p>만료는 30분이 아니라 액세스 토큰과 동일하게 24시간으로 둔다. 상세 화면을 열어둔 채
 * 만료되면 "캐시된 본문 + 만료된 URL" 조합이 생겨 에디터 재마운트가 필요해지고,
 * 이는 편집 중이던 입력을 날린다.
 */
@Slf4j
@Component
public class AttachmentTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public AttachmentTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${app.storage.url-expiry-millis:86400000}") long expirationMillis) {
        // Base64 디코드를 거치지 않는다. JwtTokenProvider와 동일한 원칙(CLAUDE.md 12장).
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    public String createViewToken(Long attachmentId, Long userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("purpose", "attachment-view")
                .claim("aid", attachmentId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMillis))
                // 인자 없는 signWith(key)는 키 길이로 알고리즘을 추론한다(CLAUDE.md 3장).
                // 반드시 알고리즘을 명시해 HS256으로 고정한다.
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 뷰 토큰을 검증하고 클레임을 꺼낸다.
     *
     * <p>{@link JwtTokenProvider#parseClaims(String)}와 동일하게, 만료·서명 불일치·형식 오류를
     * 전부 빈 값으로 바꾼다. 이 토큰은 {@code /raw} 컨트롤러가 직접 인가하므로(6장 예외 경로),
     * 필터가 아니라 호출부가 곧바로 401을 내려야 하며, 그러려면 예외보다 값으로 다루는 편이 낫다.
     */
    public Optional<Claims> parseViewClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("유효하지 않은 뷰 토큰입니다: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * 이 클레임이 {@code attachmentId}에 대한 뷰 토큰인지 확인한다.
     *
     * <p>양방향 권한 상승 방지 중 두 번째다 — 액세스 토큰이 {@code ?token=}으로 들어오거나,
     * 다른 첨부의 {@code aid}를 가진 뷰 토큰으로 접근하는 경우를 모두 걸러낸다.
     * {@code purpose}와 {@code aid} 둘 다 일치해야 통과한다.
     */
    public boolean isValidViewToken(Claims claims, Long attachmentId) {
        boolean purposeMatches = "attachment-view".equals(claims.get("purpose", String.class));
        Long aid = claims.get("aid", Long.class);
        return purposeMatches && attachmentId.equals(aid);
    }
}
