package com.example.dto;

/** 회원가입·로그인 성공 시 발급되는 JWT. */
public record TokenResponse(String token) {
}
