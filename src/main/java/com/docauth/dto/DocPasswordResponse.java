package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档密码响应")
public class DocPasswordResponse {
    @Schema(description = "解密后的密码", example = "password123")
    private String password;
}