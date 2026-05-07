package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "解密响应")
public class DecryptResponse {
    
    @Schema(description = "解密后的明文", example = "password123")
    private String decryptedText;
    
    @Schema(description = "使用的密钥版本", example = "default")
    private String keyVersion;
}
