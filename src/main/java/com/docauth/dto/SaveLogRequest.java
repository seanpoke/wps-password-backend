package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "保存操作日志请求")
public class SaveLogRequest {
    @Schema(description = "文档ID", example = "doc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String docId;
    
    @Schema(description = "文件路径", example = "/documents/test.docx", requiredMode = Schema.RequiredMode.REQUIRED)
    private String path;
    
    @Schema(description = "密钥版本号", example = "default", requiredMode = Schema.RequiredMode.REQUIRED)
    private String keyVersion;
    
    @Schema(description = "修改前密码（加密）")
    private String beforePassword;
    
    @Schema(description = "修改后密码（加密）")
    private String afterPassword;
    
    @Schema(description = "可能的密码列表（加密）")
    private List<String> possiblePasword;
    
    @Schema(description = "操作来源平台", example = "win、android")
    private String platform;
}
