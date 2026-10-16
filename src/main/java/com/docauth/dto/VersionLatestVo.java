package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 平台最新版本信息（客户端下载页展示用，免 token）。
 * 仅暴露下载页所需字段，不含后台管理用的状态/标记等。
 */
@Data
@Schema(description = "平台最新版本信息(下载页展示, 免token)")
public class VersionLatestVo {

    @Schema(description = "平台标识: win/android", example = "win")
    private String platform;

    @Schema(description = "最新版本号", example = "1.0.6")
    private String version;

    @Schema(description = "下载地址(已按公网前缀解析)", example = "/downloads/PasswordManager-1.0.6.zip")
    private String downloadUrl;

    @Schema(description = "更新说明", example = "1. 修复若干问题\\n2. 性能优化")
    private String changelog;

    @Schema(description = "发布时间(yyyy-MM-dd HH:mm:ss)", example = "2026-10-15 10:00:00")
    private String releaseTime;
}
