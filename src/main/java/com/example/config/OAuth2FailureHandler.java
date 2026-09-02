package com.example.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 구글 로그인 실패 시 프론트로 302 리다이렉트한다.
 *
 * <p>계정 충돌({@code email_conflict})은 전용 쿼리로 분기해 프론트가 안내 문구를 보여줄 수 있게 한다.
 * 그 외 실패(네트워크 오류 등)는 {@code oauth_failed} 로 뭉뚱그린다 — DoD 가 요구하지는 않지만,
 * 핸들러가 아무 응답도 쓰지 않고 끝나는 사고를 막는 안전망이다.
 */
@Component
public class OAuth2FailureHandler implements AuthenticationFailureHandler {

    private static final String EMAIL_CONFLICT_ERROR_CODE = "email_conflict";

    /** OAuth2 리다이렉트의 기준 주소. app.cors.allowed-origins 와 겸용하지 않는다(CLAUDE.md 6장). */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                         AuthenticationException exception) throws IOException {
        if (exception instanceof OAuth2AuthenticationException oae
                && EMAIL_CONFLICT_ERROR_CODE.equals(oae.getError().getErrorCode())) {
            response.sendRedirect(frontendUrl + "/login?error=email_conflict");
        } else {
            response.sendRedirect(frontendUrl + "/login?error=oauth_failed");
        }
    }
}
