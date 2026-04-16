package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "获取文档密码请求")
public class DocPasswordRequest {
    @Schema(description = "文档ID", example = "doc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String docId;
    
    @Schema(description = "RSA加密后的密码", example = "encrypted_password_here", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encryPassword;
}