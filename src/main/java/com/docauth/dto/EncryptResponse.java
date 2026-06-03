package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "加密响应")
public class EncryptResponse {

    @Schema(description = "加密后的密文", example = "xxxxx...")
    private String encryptedText;

    @Schema(description = "原始文本长度", example = "10")
    private Integer originalLength;

    @Schema(description = "加密后长度", example = "128")
    private Integer encryptedLength;

    @Schema(description = "使用的密钥版本", example = "default")
    private String keyVersion;
}
