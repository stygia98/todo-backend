package com.example.dto;

import com.example.domain.User;

/**
 * 내 정보 응답.
 *
 * <p>{@code email} 을 포함한다(AUTH-08). 화면에 표시할지는 프론트(Phase 7)의 몫이지,
 * API 가 감출 정보가 아니다 — 본인 정보이기 때문이다.
 */
public record UserResponse(Long id, String email, String nickname) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname());
    }
}
