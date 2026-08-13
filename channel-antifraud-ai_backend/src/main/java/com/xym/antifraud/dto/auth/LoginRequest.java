package com.xym.antifraud.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的11位手机号")
        String phone,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
