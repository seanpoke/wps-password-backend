package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "更新文档授权请求")
public class DocUpdateRelRequest {
    @Schema(description = "文档ID", example = "doc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String docId;

    @Schema(description = "用户ID列表（sys_user.id）")
    private List<Long> userIdList;

    @Schema(description = "部门ID列表（sys_dept.id）")
    private List<Long> deptIdList;

    @Schema(description = "是否为临时操作，true则跳过文件存在性和所有者校验", example = "false", defaultValue = "false")
    private Boolean isTemp = false;
}