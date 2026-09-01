package com.example.config;

import com.example.dto.ApiResponse;
import com.example.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증되지 않은 요청의 401 응답을 {@link ApiResponse} 포맷으로 직접 쓴다.
 *
 * <h2>왜 GlobalExceptionHandler 로 안 되는가</h2>
 *
 * <p>{@code @RestControllerAdvice} 는 <b>컨트롤러에 진입한 요청의 예외만</b> 잡는다.
 * 토큰이 없거나 만료돼 {@code JwtAuthenticationFilter} 단계에서 인증이 세팅되지 않으면
 * 요청은 컨트롤러까지 가지 못하고, Spring Security 의 기본 응답(빈 본문)이 그대로 나간다.
 * 그러면 "모든 응답이 {@code {success, data, error}} 포맷"이라는 규칙이 401 에서만 깨지고,
 * 프론트의 {@code apiClient} 가 언래핑에 실패해 에러 처리가 어긋난다.
 *
 * <h2>⚠️ ObjectMapper 는 반드시 주입받는다</h2>
 *
 * <p>이 프로젝트에는 Jackson 2({@code com.fasterxml.jackson})와
 * Jackson 3({@code tools.jackson})이 <b>둘 다 compile scope 로 들어와 있다.</b>
 * springdoc 과 jjwt-jackson 이 2를, Boot 4 가 3을 끌고 온다.
 * 따라서 잘못된 쪽을 import 해도 <b>컴파일이 통과한다.</b>
 *
 * <p>Boot 4 가 빈으로 등록하는 것은 {@code tools.jackson.databind.json.JsonMapper} 이고
 * 이는 {@code tools.jackson.databind.ObjectMapper} 의 하위 타입이다. 그래서 이 타입으로 주입받는다.
 *
 * <p><b>{@code new ObjectMapper()} 로 직접 만들지 않는다.</b> 그렇게 하면 컴파일도 기동도
 * 성공하지만 Boot 의 직렬화 설정이 빠진 인스턴스를 쓰게 되어, 나중에 날짜 포맷 같은 설정이
 * 이 응답에만 적용되지 않는 문제가 조용히 생긴다.
 *
 * <p>⚠️ 응답 포맷을 바꿀 때는 {@link JwtAccessDeniedHandler} 도 <b>함께</b> 고친다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        // 명시하지 않으면 한글 메시지가 깨진다.
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // Jackson 3 의 JacksonException 은 RuntimeException 이라 검사 예외 처리가 필요 없다.
        // (Jackson 2 의 JsonProcessingException 과 다른 점이다.)
        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.UNAUTHORIZED)));
    }
}
