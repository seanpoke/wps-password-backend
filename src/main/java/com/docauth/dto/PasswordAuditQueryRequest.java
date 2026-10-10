package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "密码审计日志查询请求")
public class PasswordAuditQueryRequest {

    @Schema(description = "文件指纹ID(uid)，模糊匹配", requiredMode = Schema.RequiredMode.REQUIRED)
    private String uid;

    @Schema(description = "文件路径，模糊匹配")
    private String path;

    @Schema(description = "操作人(createBy)，模糊匹配")
    private String user;

    @Schema(description = "平台：win / android，精确匹配；空表示全部")
    private String platform;

    @Schema(description = "开始日期 yyyy-MM-dd（必填，精确到日）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String startTime;

    @Schema(description = "结束日期 yyyy-MM-dd（必填，精确到日，含当日整天）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String endTime;

    @Schema(description = "页码，从1开始", defaultValue = "1")
    private int page = 1;

    @Schema(description = "每页大小", defaultValue = "50")
    private int size = 50;
}
