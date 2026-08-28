package com.example.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 사용자 조회.
 *
 * <p>⚠️ <b>모든 조회 메서드에 {@code AndDeletedAtIsNull} 을 붙인다.</b>
 * 소프트 삭제를 쓰므로 조건을 빠뜨리면 삭제된 계정이 그대로 조회된다.
 * 메서드를 추가할 때도 이 규칙을 지킨다.
 *
 * <p>회원 탈퇴가 이번 범위의 비목표라 {@code deleted_at} 이 실제로 채워지는 경로는 아직 없지만,
 * 조건을 지금부터 걸어두어야 향후 탈퇴 기능 추가 시 조회 로직을 다시 훑지 않아도 된다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * PK 조회. JWT 인증 필터가 {@code sub} 클레임의 사용자 id 로 호출한다.
     *
     * <p>토큰은 유효한데 사용자가 조회되지 않으면 404 가 아니라
     * <b>401 UNAUTHORIZED</b> 로 응답한다(CLAUDE.md 5장).
     */
    Optional<User> findByIdAndDeletedAtIsNull(Long id);

    /** 로그인 시 이메일로 계정을 찾는다. */
    Optional<User> findByEmailAndDeletedAtIsNull(String email);

    /** 회원가입 시 이메일 중복 검사. 중복이면 409 EMAIL_DUPLICATED 로 응답한다. */
    boolean existsByEmailAndDeletedAtIsNull(String email);
}
