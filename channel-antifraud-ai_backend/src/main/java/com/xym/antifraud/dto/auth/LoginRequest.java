package com.xym.antifraud.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank(message = "请输入手机号或用户名")
        @Size(max = 64, message = "账号不能超过64个字符")
        String account,
        @NotBlank(message = "密码不能为空")
        String password
) {
}
