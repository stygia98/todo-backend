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
 * JWT 발급과 검증. 서명 알고리즘은 <b>HS256 고정</b>이다.
 *
 * <h2>클레임 구성 (CLAUDE.md 6장)</h2>
 * <pre>
 * sub   : user.id (숫자 문자열)   ← 이메일이 아니다
 * email : user.email
 * iat   : 발급 시각
 * exp   : 발급 + 24시간
 * </pre>
 *
 * <p>{@code sub} 에 id 를 담으므로 인증 필터가 PK 조회로 끝난다.
 * 이메일을 담으면 인덱스는 있어도 PK 조회보다 느리고, 이메일 변경 기능이 생기면 토큰이 깨진다.
 *
 * <h2>⚠️ 0.11 문법을 쓰지 않는다</h2>
 *
 * <p>인터넷 예제 다수가 {@code Jwts.parser().setSigningKey(...)} 와 {@code SignatureAlgorithm.HS256}
 * 을 쓰는 0.11 문법인데, <b>이 메서드들은 0.12.6 에도 deprecated 상태로 남아 있어 컴파일이 통과한다.</b>
 * 즉 잘못 써도 오류로 걸러지지 않는다. 0.12 문법인
 * {@code verifyWith(key).build().parseSignedClaims(...)} 와
 * {@code signWith(key, Jwts.SIG.HS256)} 를 쓴다. 알고리즘 상수도 0.11 의
 * {@code SignatureAlgorithm} enum 이 아니라 0.12 의 {@code Jwts.SIG} 쪽이다.
 *
 * <h2>⚠️ 시크릿을 Base64 디코드하지 않는다</h2>
 *
 * <p>raw UTF-8 바이트를 그대로 키로 쓴다. Base64 로 디코드하면 32자 문자열이 24바이트가 되어
 * HS256 의 최소 길이(32바이트)에 미달해 {@code WeakKeyException} 이 난다(CLAUDE.md 12장).
 * 키를 생성자에서 미리 만들므로, 시크릿이 짧으면 <b>기동 시점에</b> 실패한다.
 * 첫 로그인 요청에서야 터지는 것보다 낫다.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long expirationMillis;

    public JwtTokenProvider(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration}") long expirationMillis) {
        // Base64 디코드를 거치지 않는다. 짧으면 여기서 WeakKeyException 으로 기동이 멈춘다.
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMillis = expirationMillis;
    }

    /**
     * 토큰 발급.
     *
     * @param userId {@code sub} 에 담길 사용자 id
     * @param email  {@code email} 클레임
     */
    public String createToken(Long userId, String email) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + expirationMillis);

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(now)
                .expiration(expiresAt)
                // ⚠️ 알고리즘을 반드시 명시한다. 인자 없는 signWith(key) 는 jjwt 가 키 길이로
                // 알고리즘을 추론해, 시크릿이 48~63바이트면 HS384, 64바이트 이상이면 HS512 가 된다.
                // 즉 코드가 아니라 JWT_SECRET 의 길이가 알고리즘을 결정하게 되어,
                // 환경마다 시크릿 길이가 다르면 로컬과 운영의 알고리즘이 갈린다.
                // CLAUDE.md 12장이 "HS256 으로 고정한다"고 규정한 이유가 이것이다.
                // (Phase 3 DoD 검증 중 49바이트 시크릿에서 실제로 alg=HS384 가 발급되어 발견했다)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 토큰을 검증하고 클레임을 꺼낸다.
     *
     * <p>만료·서명 불일치·형식 오류를 <b>전부 빈 값으로 바꾼다.</b> 예외를 던지지 않는 이유는,
     * 호출부인 {@code JwtAuthenticationFilter} 가 인증을 세팅하지 않고 체인을 계속 진행시키면
     * {@code authorizeHttpRequests} 의 {@code authenticated()} 가 걸러
     * {@code JwtAuthenticationEntryPoint} 로 넘겨주기 때문이다.
     * 필터가 직접 응답을 쓰는 것보다 흐름이 단순하고, 401 응답 포맷이 한 곳에서만 관리된다.
     *
     * @return 유효하면 클레임, 아니면 {@link Optional#empty()}
     */
    public Optional<Claims> parseClaims(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // 만료된 토큰은 정상 운영 중에도 발생한다. 스택트레이스까지 남기지 않는다.
            log.debug("유효하지 않은 토큰입니다: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    /**
     * {@code sub} 클레임을 사용자 id 로 변환한다.
     *
     * <p>{@code sub} 가 숫자가 아니면(위조되었거나 다른 시스템의 토큰이면) 빈 값을 반환한다.
     * {@link NumberFormatException} 이 필터 밖으로 전파되면 500 이 나간다.
     */
    public Optional<Long> extractUserId(Claims claims) {
        try {
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (NumberFormatException | NullPointerException e) {
            log.debug("sub 클레임을 사용자 id 로 해석하지 못했습니다.");
            return Optional.empty();
        }
    }
}
