package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Map;

/**
 * 版本列表分页返回（admin）：分页数据 + 状态汇总（汇总不受状态筛选影响）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "版本列表分页返回")
public class VersionPageVo extends PageResult<AppVersionDto> {

    @Schema(description = "当前平台范围内全部版本数（不受状态筛选影响）", example = "5")
    private long scopeTotal;

    @Schema(description = "最新版本号（仅指定 platform 时返回）", example = "1.1.2")
    private String latestVersion;

    @Schema(description = "最低支持版本号（仅指定 platform 时返回）", example = "1.0.1")
    private String minVersion;

    @Schema(description = "各派生状态计数（不受状态筛选影响）: available/expired/unreleased")
    private Map<String, Long> statusCounts;

    public VersionPageVo(java.util.List<AppVersionDto> list, long total, int page, int size) {
        super(list, total, page, size);
    }
}
