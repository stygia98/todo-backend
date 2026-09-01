package com.example.controller;

import com.example.domain.User;
import com.example.dto.ApiResponse;
import com.example.dto.LoginRequest;
import com.example.dto.SignupRequest;
import com.example.dto.TokenResponse;
import com.example.dto.UserResponse;
import com.example.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.example.config.OpenApiConfig.SECURITY_SCHEME_NAME;

/**
 * 인증 API. CLAUDE.md 5장의 세 엔드포인트만 제공한다.
 *
 * <p>⚠️ <b>로그아웃 API 는 만들지 않는다.</b> Refresh Token 과 토큰 블랙리스트가 없으므로,
 * 서버가 할 수 있는 일이 없다. 로그아웃은 프론트가 localStorage 의 토큰을 지우고
 * React Query 캐시를 비운 뒤 {@code /login} 으로 이동하는 것으로 끝난다(Phase 7).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 회원가입.
     *
     * <p>⚠️ 상태 코드는 <b>200</b>이다. REST 관례상 리소스 생성은 201 이지만,
     * ROADMAP Phase 3 DoD 1 이 "200/409 로 응답"이라고 명시적으로 못박고 있어 그대로 따른다.
     */
    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<TokenResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.signup(request)));
    }

    /** 로그인. 실패 사유(미가입/비밀번호 오류)는 응답에서 구분되지 않는다(PRD 5.1). */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request.email(), request.password())));
    }

    /**
     * 내 정보 조회.
     *
     * <p>{@code user} 는 {@code JwtAuthenticationFilter} 가 이미 조회해 SecurityContext 에
     * 넣어둔 것을 그대로 받는다. 여기서 다시 DB 를 조회하지 않는다.
     */
    @GetMapping("/me")
    @SecurityRequirement(name = SECURITY_SCHEME_NAME)
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }
}
