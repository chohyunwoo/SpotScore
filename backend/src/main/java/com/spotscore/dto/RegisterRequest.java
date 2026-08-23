package com.spotscore.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 회원가입 요청. 비밀번호 상한(72)은 BCrypt가 72바이트까지만 해싱에 사용하는
 * 한계에 맞춘 것 - 그 이상은 조용히 잘려 보안 오해를 부르므로 입력 단계에서 막는다.
 */
public record RegisterRequest(
        @NotBlank(message = "이메일은 필수입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수입니다.")
        @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
        String password,

        @NotBlank(message = "표시 이름은 필수입니다.")
        @Size(max = 50, message = "표시 이름은 50자 이하여야 합니다.")
        String displayName) {
}
