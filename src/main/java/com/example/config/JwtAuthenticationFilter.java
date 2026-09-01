package com.example.config;

import com.example.domain.User;
import com.example.domain.UserRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * {@code Authorization: Bearer ...} 헤더를 해석해 {@link SecurityContextHolder} 를 채운다.
 *
 * <h2>⚠️ 이 클래스에 {@code @Component} 를 붙이지 않는다</h2>
 *
 * <p>Spring Boot 는 {@code Filter} 타입 빈을 발견하면 <b>서블릿 컨테이너에도 자동 등록</b>한다.
 * {@code @Component} 를 붙이면 Security 필터 체인과 서블릿 체인 <b>양쪽에 등록</b>되어,
 * Security 가 보호하지 않는 경로에서도 이 필터가 실행된다.
 * {@code OncePerRequestFilter} 라 한 요청에 두 번 실행되지는 않지만, 등록 자체가 의도 밖이다.
 *
 * <p>그래서 {@code SecurityConfig} 가 직접 인스턴스를 만들어
 * {@code addFilterBefore} 로 한 곳에만 등록한다.
 *
 * <h2>인증 실패 시 응답을 쓰지 않는다</h2>
 *
 * <p>토큰이 없거나 유효하지 않으면 <b>아무것도 하지 않고 체인을 계속 진행</b>시킨다.
 * 보호된 경로라면 {@code authorizeHttpRequests} 의 {@code authenticated()} 가 걸러
 * {@code JwtAuthenticationEntryPoint} 로 넘긴다. 401 응답 포맷이 한 곳에서만 관리된다.
 *
 * <p>이 설계 덕분에 <b>토큰은 유효한데 사용자가 조회되지 않는 경우</b>도 자연히 401 이 된다.
 * 404 가 아니다(CLAUDE.md 5장). 계정이 사라진 상태의 토큰은 인증되지 않은 것과 같이 다룬다.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        resolveToken(request)
                .flatMap(jwtTokenProvider::parseClaims)
                .flatMap(this::findActiveUser)
                .ifPresent(this::authenticate);

        // 인증 성공 여부와 무관하게 항상 체인을 계속 진행시킨다.
        // 여기서 중단하면 permitAll 경로까지 막힌다.
        filterChain.doFilter(request, response);
    }

    /** {@code Bearer } 접두사를 떼어 토큰만 꺼낸다. */
    private Optional<String> resolveToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /**
     * 클레임의 {@code sub} 로 사용자를 찾는다.
     *
     * <p>{@code deleted_at IS NULL} 조건이 붙은 조회 메서드를 쓴다.
     * 소프트 삭제된 계정의 토큰이 남아 있어도 인증되지 않는다.
     */
    private Optional<User> findActiveUser(Claims claims) {
        return jwtTokenProvider.extractUserId(claims)
                .flatMap(userRepository::findByIdAndDeletedAtIsNull);
    }

    /**
     * 인증 정보를 SecurityContext 에 넣는다.
     *
     * <p>principal 로 {@link User} 엔티티를 넣는다. 컨트롤러가
     * {@code @AuthenticationPrincipal User user} 로 바로 받아 쓸 수 있어 재조회가 없다.
     * {@code User} 에는 LAZY 연관이 없어 영속성 컨텍스트 밖에서 참조해도 안전하다.
     * Phase 5 에서 {@code User} 에 연관이 추가되면 이 결정을 다시 검토한다.
     *
     * <p>권한 목록은 비워둔다. 이 앱에는 역할 구분이 없고 본인 데이터만 다룬다.
     */
    private void authenticate(User user) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
