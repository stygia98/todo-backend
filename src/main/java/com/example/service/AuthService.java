package com.example.service;

import com.example.domain.User;
import com.example.domain.UserRepository;
import com.example.dto.SignupRequest;
import com.example.dto.TokenResponse;
import com.example.config.JwtTokenProvider;
import com.example.exception.BusinessException;
import com.example.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입과 로그인.
 *
 * <h2>⚠️ 로그인 실패는 원인과 무관하게 단 하나의 예외로 던진다</h2>
 *
 * <p>미가입 이메일과 비밀번호 오류를 구분해서 응답하면 계정 존재 여부가 노출된다(PRD 5.1).
 * "메시지 두 개를 만든 뒤 같게 맞춘다"는 접근을 쓰지 않는다. 한쪽만 나중에 수정되면
 * 조용히 어긋나기 때문이다. 대신 {@link ErrorCode#UNAUTHORIZED} 하나만 던져서,
 * 코드와 메시지가 같다는 사실이 <b>애초에 갈릴 수 없는 구조</b>가 되게 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 이메일 가입.
     *
     * <p>비밀번호는 여기서 해싱한다. {@code SignupRequest} 는 평문을 들고 있을 뿐,
     * 해싱 책임을 컨트롤러나 DTO 에 넘기지 않는다.
     */
    @Transactional
    public TokenResponse signup(SignupRequest request) {
        if (userRepository.existsByEmailAndDeletedAtIsNull(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = User.createLocal(request.email(), encodedPassword, request.nickname());
        userRepository.save(user);

        return new TokenResponse(jwtTokenProvider.createToken(user.getId(), user.getEmail()));
    }

    /**
     * 로그인.
     *
     * <p>조회 실패, 소셜 전용 계정(비밀번호 null), 비밀번호 불일치를 <b>전부 같은 예외</b>로 던진다.
     * 어느 경로로 실패했는지는 로그에서만 구분한다(감사 목적). 응답에는 드러나지 않는다.
     */
    public TokenResponse login(String email, String rawPassword) {
        User user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));

        // 소셜 전용 계정은 password 가 null 이다.
        // BCryptPasswordEncoder(정확히는 AbstractValidatingPasswordEncoder.matches)는
        // encodedPassword 가 null 이면 예외 없이 false 를 반환하도록 이미 구현되어 있어
        // 아래 null 검사가 없어도 동작은 같다(javap 로 직접 확인했다).
        // 그래도 남겨두는 이유는 "왜 소셜 계정이 로그인에 실패하는가"를 매처 내부까지
        // 따라가지 않고 이 줄만 보고 알 수 있게 하기 위해서다.
        if (user.getPassword() == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return new TokenResponse(jwtTokenProvider.createToken(user.getId(), user.getEmail()));
    }
}
