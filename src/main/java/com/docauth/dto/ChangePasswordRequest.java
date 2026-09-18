package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "修改密码请求(本地用户)")
public class ChangePasswordRequest {

    @Schema(description = "原密码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String oldPassword;

    @Schema(description = "新密码", example = "newpass", requiredMode = Schema.RequiredMode.REQUIRED)
    private String newPassword;
}
