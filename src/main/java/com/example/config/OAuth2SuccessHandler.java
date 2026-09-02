package com.example.config;

import com.example.domain.User;
import com.example.domain.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 구글 로그인 성공 시 JWT 를 발급해 프론트로 302 리다이렉트한다.
 *
 * <p>커스텀 principal 클래스를 따로 만들지 않고, 인증된 {@link OidcUser} 의 email 로
 * {@link UserRepository} 를 재조회해 id 를 얻는다. {@code AuthService.login()} 과 같은 패턴이다.
 */
@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;

    /** OAuth2 리다이렉트의 기준 주소. app.cors.allowed-origins 와 겸용하지 않는다(CLAUDE.md 6장). */
    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                         Authentication authentication) throws IOException {
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();

        // CustomOAuth2UserService.processOidcUser 가 이미 저장을 보장했으므로,
        // 여기서 조회되지 않는다면 있을 수 없는 상황이다 — 버그로 간주해 그대로 500을 낸다.
        User user = userRepository.findByEmailAndDeletedAtIsNull(oidcUser.getEmail())
                .orElseThrow(() -> new IllegalStateException(
                        "OAuth2 인증은 성공했으나 사용자를 찾을 수 없습니다: " + oidcUser.getEmail()));

        String token = jwtTokenProvider.createToken(user.getId(), user.getEmail());
        String redirectUrl = frontendUrl + "/oauth/callback?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);

        response.sendRedirect(redirectUrl);
    }
}
