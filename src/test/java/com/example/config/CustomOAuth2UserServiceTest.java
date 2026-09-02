package com.example.config;

import com.example.domain.AuthProvider;
import com.example.domain.User;
import com.example.domain.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link CustomOAuth2UserService} 단위 테스트. CLAUDE.md 14장 테스트 8번.
 *
 * <p>OAuth2 흐름은 실제 구글 서버와 통신하므로 {@code MockMvc} 로 끝까지 검증할 수 없다.
 * 대신 {@link CustomOAuth2UserService#processOidcUser} 를 <b>같은 패키지에서 직접 호출</b>해
 * 네트워크 없이 판정 로직만 검증한다. {@link OidcIdToken} 에 email·name 클레임을 심어 만든
 * {@link DefaultOidcUser} 가 실제 구글 응답을 대신하는 테스트 더블이다.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@DisplayName("CustomOAuth2UserService 단위 테스트")
class CustomOAuth2UserServiceTest {

    @Autowired
    private CustomOAuth2UserService customOAuth2UserService;

    @Autowired
    private UserRepository userRepository;

    /** name 클레임까지 채운 OidcUser 더블. */
    private OidcUser oidcUser(String email, String name) {
        OidcIdToken.Builder builder = OidcIdToken.withTokenValue("test-token")
                .subject("google-sub-" + email)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", email);
        if (name != null) {
            builder.claim("name", name);
        }
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), builder.build());
    }

    /** name 클레임이 없는 OidcUser 더블. Map 에 키 자체가 없어야 getFullName() 이 null 을 반환한다. */
    private OidcUser oidcUserWithoutName(String email) {
        return oidcUser(email, null);
    }

    @Test
    @DisplayName("신규 이메일이면 provider=GOOGLE 로 가입된다")
    void newEmailCreatesGoogleAccount() {
        String email = "new-google@example.com";

        customOAuth2UserService.processOidcUser(oidcUser(email, "테스트유저"));

        User saved = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getNickname()).isEqualTo("테스트유저");
    }

    @Test
    @DisplayName("기존 GOOGLE 계정으로 재로그인해도 중복 생성되지 않는다")
    void existingGoogleAccountDoesNotDuplicate() {
        String email = "repeat-google@example.com";
        userRepository.save(User.createGoogle(email, "기존닉네임", "google-sub-existing"));

        customOAuth2UserService.processOidcUser(oidcUser(email, "기존닉네임"));

        long count = userRepository.findAll().stream()
                .filter(u -> u.getEmail().equals(email))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("동일 이메일의 LOCAL 계정이 있으면 email_conflict 예외를 던진다")
    void localAccountConflictThrows() {
        String email = "conflict@example.com";
        userRepository.save(User.createLocal(email, "{bcrypt}hash", "로컬유저"));

        assertThatThrownBy(() -> customOAuth2UserService.processOidcUser(oidcUser(email, "구글이름")))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo("email_conflict");
    }

    @Test
    @DisplayName("name 클레임이 없으면 nickname은 이메일의 @ 앞부분이 된다")
    void nicknameFallsBackToEmailPrefixWhenNameMissing() {
        String email = "noname@example.com";

        customOAuth2UserService.processOidcUser(oidcUserWithoutName(email));

        User saved = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        assertThat(saved.getNickname()).isEqualTo("noname");
    }

    @Test
    @DisplayName("name이 50자를 넘으면 nickname은 50자로 절삭된다")
    void nicknameTruncatedTo50Characters() {
        String email = "longname@example.com";
        String longName = "가".repeat(60);

        customOAuth2UserService.processOidcUser(oidcUser(email, longName));

        User saved = userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow();
        assertThat(saved.getNickname()).hasSize(50);
    }
}
