package com.example.domain;

/**
 * 계정의 인증 제공자.
 *
 * <p>같은 이메일로 LOCAL 계정이 이미 있으면 GOOGLE 로그인을 거부한다(계정 탈취 방지).
 * 자동 연동하지 않으며 별도 계정도 만들지 않는다. 상세는 CLAUDE.md 6장 참조.
 */
public enum AuthProvider {

    /** 이메일 + 비밀번호로 가입한 계정. password 가 반드시 있다. */
    LOCAL,

    /** 구글 OAuth2 로 가입한 계정. password 가 null 이다. */
    GOOGLE
}
