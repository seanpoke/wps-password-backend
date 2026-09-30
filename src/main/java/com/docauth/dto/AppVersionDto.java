package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 客户端版本（按平台多版本），用于 admin 列表/新增/更新
 */
@Data
@Schema(description = "客户端版本(按平台多版本)")
public class AppVersionDto {

    @Schema(description = "版本记录ID（更新/标记/删除时使用）", example = "1")
    private Long id;

    @Schema(description = "平台标识: win/android", example = "win", requiredMode = Schema.RequiredMode.REQUIRED)
    private String platform;

    @Schema(description = "版本号，如 1.0.0", example = "1.2.0", requiredMode = Schema.RequiredMode.REQUIRED)
    private String version;

    @Schema(description = "下载地址", example = "https://example.com/setup.exe")
    private String downloadUrl;

    @Schema(description = "更新说明", example = "修复若干已知问题")
    private String changelog;

    @Schema(description = "发布时间(yyyy-MM-dd HH:mm:ss)", example = "2026-09-20 10:00:00")
    private String releaseTime;

    @Schema(description = "最后修改时间(yyyy-MM-dd HH:mm:ss)，数据库自动维护", example = "2026-09-29 17:30:00")
    private String updateTime;

    @Schema(description = "是否最低支持版本", example = "false")
    private Boolean isMin = false;

    @Schema(description = "是否最新版本", example = "false")
    private Boolean isLatest = false;

    @Schema(description = "状态: 1启用 0停用", example = "1")
    private Integer status = 1;

    @Schema(description = "派生状态集合(admin 列表返回): latest/lowest/available/expired/unreleased",
            example = "[\"latest\"]")
    private java.util.List<String> states;
}
