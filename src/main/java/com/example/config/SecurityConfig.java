package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 설정.
 *
 * <p>Phase 1 범위는 <b>인가 경로와 stateless 골격</b>까지다.
 * JWT 인증 필터, AuthenticationEntryPoint, AccessDeniedHandler, CORS, PasswordEncoder는
 * Phase 3에서 추가한다.
 *
 * <p>이 클래스가 없으면 Spring Security 자동 설정이 모든 요청에 formLogin을 걸어
 * {@code /swagger-ui/index.html} 이 200이 아니라 302(→ /login)로 응답한다.
 */
@Configuration
@EnableWebSecurity
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

                // authorizeRequests()는 Spring Security 7에서 제거되었다. 반드시 authorizeHttpRequests()를 쓴다.
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated()
                );

        return http.build();
    }
}
