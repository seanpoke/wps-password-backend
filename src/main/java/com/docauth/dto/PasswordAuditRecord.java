package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "密码审计记录(从日志文件解析, 不入库)")
public class PasswordAuditRecord {

    @Schema(description = "时间戳(含毫秒)")
    private String timestamp;

    @Schema(description = "文件指纹ID")
    private String uid;

    @Schema(description = "文件路径")
    private String path;

    @Schema(description = "修改前密码(密文)")
    private String beforePassword;

    @Schema(description = "修改后密码(密文)")
    private String afterPassword;

    @Schema(description = "可能的密码列表(密文)")
    private List<String> possiblePasswords;

    @Schema(description = "平台 win/android")
    private String platform;

    @Schema(description = "操作人")
    private String createBy;

    @Schema(description = "密钥版本")
    private String keyVersion;
}
