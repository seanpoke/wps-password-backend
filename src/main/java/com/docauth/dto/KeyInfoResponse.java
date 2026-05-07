package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 密钥信息响应DTO
 */
@Data
@Schema(description = "密钥信息响应")
public class KeyInfoResponse {

    @Schema(description = "密钥版本", example = "v1.0")
    private String keyVersion;

    @Schema(description = "公钥内容", example = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE...")
    private String publicKey;
}
