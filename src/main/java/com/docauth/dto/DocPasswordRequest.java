package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "获取文档密码请求")
public class DocPasswordRequest {
    @Schema(description = "文档ID", example = "doc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String docId;

    @Schema(description = "ECC加密后的密码", example = "encrypted_password_here", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encryPassword;

    @Schema(description = "公私钥版本", example = "default", defaultValue = "default")
    private String keyVersion = "default";

    @Schema(description = "是否为临时操作，true则跳过文件存在性和所有者校验", example = "false", defaultValue = "false")
    private Boolean isTemp = false;
}