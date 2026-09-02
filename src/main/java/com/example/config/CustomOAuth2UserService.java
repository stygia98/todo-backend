package com.example.config;

import com.example.domain.AuthProvider;
import com.example.domain.User;
import com.example.domain.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 구글 OIDC 로그인 판정.
 *
 * <p>구글 기본 scope 에 openid 가 포함돼 OIDC 흐름이므로 {@link OidcUserService} 를 상속한다
 * (일반 OAuth2 인 {@code DefaultOAuth2UserService} 가 아니다).
 *
 * <p>{@link #loadUser} 는 {@code super.loadUser()} 로 실제 구글 서버와 통신해 서명·만료 검증까지
 * 끝낸다. 판정 로직(신규가입/기존조회/충돌거부/nickname 결정)은 {@link #processOidcUser} 로
 * 분리했다 — 단위 테스트가 네트워크 없이 이 메서드를 직접 호출할 수 있게 하기 위해서다
 * (CLAUDE.md 14장 테스트 8번).
 */
@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends OidcUserService {

    private static final int NICKNAME_MAX_LENGTH = 50;

    private final UserRepository userRepository;

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);
        return processOidcUser(oidcUser);
    }

    /**
     * 신규가입/기존조회/충돌거부 세 분기를 판정한다.
     *
     * <p>{@code super.loadUser()} 의 네트워크 I/O 는 이 메서드 밖에서 이미 끝났으므로,
     * DB 쓰기만 트랜잭션으로 묶는다.
     */
    @Transactional
    OidcUser processOidcUser(OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        Optional<User> existing = userRepository.findByEmailAndDeletedAtIsNull(email);

        if (existing.isEmpty()) {
            String nickname = resolveNickname(oidcUser.getFullName(), email);
            userRepository.save(User.createGoogle(email, nickname, oidcUser.getSubject()));
        } else if (existing.get().getProvider() == AuthProvider.LOCAL) {
            // 같은 이메일의 로컬 계정이 이미 있으면 거부한다. 자동 연동하지 않는다
            // (구글이 준 이메일만 믿고 기존 계정 접근을 허용하면 계정 탈취 경로가 된다).
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("email_conflict"), "이미 이메일로 가입된 계정입니다.");
        }
        // provider == GOOGLE 인 기존 계정이면 아무것도 하지 않고 통과한다(재로그인, 중복 생성 방지).

        return oidcUser;
    }

    /** 구글이 준 name → 없으면 이메일의 @ 앞부분 → 50자 초과 시 절삭 (CLAUDE.md 6장). */
    private String resolveNickname(String name, String email) {
        String nickname = (name != null && !name.isBlank()) ? name : email.substring(0, email.indexOf('@'));
        return nickname.length() > NICKNAME_MAX_LENGTH ? nickname.substring(0, NICKNAME_MAX_LENGTH) : nickname;
    }
}
