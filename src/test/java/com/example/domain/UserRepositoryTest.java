package com.example.domain;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link UserRepository} 단위 테스트.
 *
 * <p>애노테이션 세 개가 모두 필요하다.
 * <ul>
 *   <li>{@code @DataJpaTest} — JPA 슬라이스만 로드</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — <b>필수.</b> 없으면 DataSource 를
 *       임베디드 DB 로 교체하려 한다. 이 프로젝트는 H2 를 쓰지 않으므로 교체되면 실패한다</li>
 *   <li>{@code @ActiveProfiles("test")} — todolist_test 를 바라보게 한다</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("이메일로 사용자를 조회한다")
    void findByEmail() {
        userRepository.save(User.createLocal("user@example.com", "{bcrypt}hash", "테스터"));

        Optional<User> found = userRepository.findByEmailAndDeletedAtIsNull("user@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getNickname()).isEqualTo("테스터");
        assertThat(found.get().getProvider()).isEqualTo(AuthProvider.LOCAL);
    }

    @Test
    @DisplayName("이메일 중복 검사가 동작한다")
    void existsByEmail() {
        userRepository.save(User.createLocal("dup@example.com", "{bcrypt}hash", "테스터"));

        assertThat(userRepository.existsByEmailAndDeletedAtIsNull("dup@example.com")).isTrue();
        assertThat(userRepository.existsByEmailAndDeletedAtIsNull("none@example.com")).isFalse();
    }

    @Test
    @DisplayName("소프트 삭제된 사용자는 조회에서 제외된다")
    void softDeletedUserIsExcluded() {
        User user = userRepository.save(User.createLocal("gone@example.com", "{bcrypt}hash", "탈퇴자"));

        user.softDelete();
        entityManager.flush();
        entityManager.clear();

        // deleted_at 이 채워졌으므로 조회 조건에서 걸러진다
        assertThat(userRepository.findByEmailAndDeletedAtIsNull("gone@example.com")).isEmpty();
        assertThat(userRepository.existsByEmailAndDeletedAtIsNull("gone@example.com")).isFalse();
        // 물리 삭제가 아니므로 PK 로는 여전히 존재한다
        assertThat(userRepository.findById(user.getId())).isPresent();
    }

    @Test
    @DisplayName("구글 계정은 비밀번호 없이 저장된다")
    void googleUserHasNoPassword() {
        User saved = userRepository.save(User.createGoogle("g@example.com", "구글사용자", "google-123"));

        assertThat(saved.getPassword()).isNull();
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getProviderId()).isEqualTo("google-123");
    }

    @Test
    @DisplayName("저장 시 created_at 과 updated_at 이 자동으로 기록된다")
    void auditingFieldsArePopulated() {
        User saved = userRepository.save(User.createLocal("audit@example.com", "{bcrypt}hash", "테스터"));

        // @EnableJpaAuditing 이 @Configuration 클래스에 잘못 붙어 있으면
        // @DataJpaTest 가 로드하지 않아 여기서 null 이 된다. 배치 오류를 검출하는 단정이다.
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }
}
