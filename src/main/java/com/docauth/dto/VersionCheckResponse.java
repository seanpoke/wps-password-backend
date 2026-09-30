package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 版本检查结果（客户端启动时调用）
 */
@Data
@Schema(description = "版本检查结果")
public class VersionCheckResponse {

    @Schema(description = "平台标识")
    private String platform;

    @Schema(description = "客户端当前版本")
    private String currentVersion;

    @Schema(description = "最新版本")
    private String latestVersion;

    @Schema(description = "最低支持版本")
    private String minVersion;

    @Schema(description = "更新类型: NONE(无需更新)/OPTIONAL(可选更新)/FORCE(强制更新)", example = "OPTIONAL")
    private String updateType;

    @Schema(description = "下载地址")
    private String downloadUrl;

    @Schema(description = "更新说明")
    private String changelog;

    @Schema(description = "最新版本发布时间(yyyy-MM-dd HH:mm:ss)")
    private String releaseTime;
}
