package com.example.dto;

import com.example.validation.MaxByteLength;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. CLAUDE.md 4장 입력값 제약 표와 1:1로 대응한다.
 *
 * <p>⚠️ {@code password} 에 {@code @Size(max=...)} 를 두지 않는다.
 * 최소 길이는 문자 수({@code @Size(min=6)})로, 최대 길이는 바이트({@code @MaxByteLength(72)})로
 * 나눠서 검증한다. 문자 수 상한을 쓰면 한글 25자(75바이트)가 통과해버려
 * BCrypt 인코딩 단계에서 500 이 난다. 자세한 이유는 {@link MaxByteLength} 참조.
 */
public record SignupRequest(

        @NotBlank(message = "이메일을 입력해 주세요.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        @Size(max = 255, message = "이메일은 255자를 넘을 수 없습니다.")
        String email,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 6, message = "비밀번호는 6자 이상이어야 합니다.")
        @MaxByteLength(value = 72, message = "비밀번호가 너무 깁니다. (한글은 1자가 3바이트로 계산됩니다)")
        String password,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 1, max = 50, message = "닉네임은 1~50자여야 합니다.")
        String nickname
) {
}
