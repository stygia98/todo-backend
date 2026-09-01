package com.example.config;

import com.example.dto.ApiResponse;
import com.example.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 인증은 되었으나 권한이 없는 요청의 403 응답을 {@link ApiResponse} 포맷으로 직접 쓴다.
 *
 * <p>{@link JwtAuthenticationEntryPoint} 와 같은 이유로 필요하다. 자세한 배경은 그쪽 주석을 참조한다.
 *
 * <p>이 앱에는 역할 구분이 없어 실제로 403 이 나가는 경로는 아직 없다.
 * 소유권 불일치도 403 이 아니라 <b>404</b> 로 응답한다(리소스 존재 여부를 노출하지 않기 위해서다).
 * 그래도 핸들러를 등록해 두는 이유는, 등록하지 않으면 Security 기본 응답이 나가
 * 응답 포맷 규칙이 깨지기 때문이다.
 *
 * <p>⚠️ 응답 포맷을 바꿀 때는 {@link JwtAuthenticationEntryPoint} 도 <b>함께</b> 고친다.
 */
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        response.getWriter().write(
                objectMapper.writeValueAsString(ApiResponse.error(ErrorCode.FORBIDDEN)));
    }
}
