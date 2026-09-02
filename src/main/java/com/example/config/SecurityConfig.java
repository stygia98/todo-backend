package com.example.config;

import com.example.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Spring Security 설정.
 *
 * <p>이 클래스가 없으면 Spring Security 자동 설정이 모든 요청에 formLogin 을 걸어
 * {@code /swagger-ui/index.html} 이 200이 아니라 302(→ /login)로 응답한다.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /** 인증 없이 접근할 수 있는 경로. CLAUDE.md 6장의 목록과 일치시킨다. */
    private static final String[] PUBLIC_PATHS = {
            "/api/v1/auth/signup",
            "/api/v1/auth/login",
            "/oauth2/**",
            "/login/oauth2/**",
            // Swagger를 빼먹으면 Phase 1 DoD가 Phase 3에서 조용히 회귀한다.
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/error"
    };

    /** CORS 허용 메서드. CLAUDE.md 6장 목록 그대로다. */
    private static final List<String> ALLOWED_METHODS =
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    /**
     * CORS 허용 헤더.
     *
     * <p>⚠️ <b>기본값에 의존하지 않고 명시한다.</b> {@code Authorization} 이 빠지면
     * 프리플라이트에서 막혀 토큰을 실은 요청이 전부 실패한다.
     */
    private static final List<String> ALLOWED_HEADERS =
            List.of("Authorization", "Content-Type");

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    /**
     * CORS 허용 오리진. <b>쉼표로 구분된 목록</b>이다.
     *
     * <p>⚠️ {@code app.frontend-url}(단일 URL)과 <b>겸용하지 않는다.</b>
     * 그쪽은 OAuth2 리다이렉트 주소를 조립하는 데 쓰여 쉼표 목록이 들어가면 주소가 깨진다.
     * 로컬에서는 두 값이 같아 정상 동작하고 <b>운영에서만 발현</b>하는 종류의 결함이다(CLAUDE.md 6장).
     */
    @Value("${app.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // CSRF 비활성화. Authorization 헤더로 인증하는 stateless API라
                // CSRF 토큰을 발급하는 경로 자체가 없다. 켜두면 POST /auth/signup 부터 403이 된다.
                .csrf(AbstractHttpConfigurer::disable)

                // 세션을 만들지 않는다. 명시하지 않으면 JSESSIONID가 발급되어
                // 토큰 기반 설계와 세션 상태가 뒤섞인다.
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // authorizeRequests()는 Spring Security 7에서 제거되었다. 반드시 authorizeHttpRequests()를 쓴다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                )

                // 필터 단계에서 거부된 401·403도 ApiResponse 포맷으로 나가게 한다.
                // 등록하지 않으면 Security 기본 응답(빈 본문)이 나가 응답 포맷 규칙이 깨진다.
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )

                // 구글 OIDC 로그인. userInfoEndpoint 에 oidcUserService 를 지정해야
                // CustomOAuth2UserService 가 호출된다(구글 기본 scope 에 openid 가 포함돼 OIDC 흐름이다).
                .oauth2Login(oauth2 -> oauth2
                        .userInfoEndpoint(u -> u.oidcUserService(customOAuth2UserService))
                        .successHandler(oAuth2SuccessHandler)
                        .failureHandler(oAuth2FailureHandler)
                )

                .addFilterBefore(jwtAuthenticationFilter(),
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * JWT 인증 필터.
     *
     * <p>⚠️ <b>빈으로 등록하지 않고 여기서 직접 만든다.</b> Spring Boot 는 {@code Filter} 타입 빈을
     * 서블릿 컨테이너에도 자동 등록하므로, {@code @Component} 를 붙이면 Security 필터 체인과
     * 서블릿 체인 <b>양쪽에</b> 등록되어 Security 가 보호하지 않는 경로에서도 실행된다.
     * 여기서 생성하면 등록 지점이 한 곳으로 고정된다.
     */
    private JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtTokenProvider, userRepository);
    }

    /** 비밀번호 해싱. BCrypt 의 입력 한계는 72바이트이며 검증은 {@code @MaxByteLength} 가 담당한다. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * CORS 설정.
     *
     * <p>쿠키를 쓰지 않으므로 {@code allowCredentials} 는 false 다.
     * true 로 두면 {@code allowedOrigins} 에 와일드카드를 쓸 수 없게 되는 제약도 함께 붙는다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // 쉼표 목록을 분리한다. 운영에서 Amplify 브랜치 도메인과 커스텀 도메인을 동시에 허용해야 한다.
        configuration.setAllowedOrigins(
                Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(origin -> !origin.isEmpty())
                        .toList());
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
