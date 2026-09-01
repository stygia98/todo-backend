package com.example.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 로그인 요청.
 *
 * <p>⚠️ {@code @Email} 이나 길이 제약을 걸지 않는다. {@code @NotBlank} 만 건다.
 * 로그인은 회원가입과 달리 검증 실패 사유가 계정 존재 여부를 암시할 수 있는 경로다.
 * 예를 들어 이메일 형식이 아니라는 400 과 자격 증명이 틀렸다는 401 이 다르게 나가면,
 * 공격자가 400/401 여부만으로 "이 값이 가입 가능한 형식인가"를 탐색하는 여지가 생긴다.
 * 형식과 무관하게 값만 있으면 {@link com.example.service.AuthService} 로 넘겨
 * 존재하지 않는 이메일과 동일하게 처리한다(PRD 5.1).
 */
public record LoginRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        String password
) {
}
