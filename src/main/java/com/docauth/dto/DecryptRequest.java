package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "私钥解密请求")
public class DecryptRequest {
    
    @Schema(description = "ECC加密后的密文", example = "base64_encoded_encrypted_string", requiredMode = Schema.RequiredMode.REQUIRED)
    private String encryptedText;
    
    @Schema(description = "密钥版本，默认为default", example = "default", defaultValue = "default")
    private String keyVersion = "default";
}
