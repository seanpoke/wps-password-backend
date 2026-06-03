package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "公钥加密请求")
public class EncryptRequest {

    @Schema(description = "待加密的原始字符串", example = "test123", requiredMode = Schema.RequiredMode.REQUIRED)
    private String text;

    @Schema(description = "密钥版本，默认为default，可使用latest获取最新密钥", example = "default", defaultValue = "default")
    private String keyVersion = "default";
}
