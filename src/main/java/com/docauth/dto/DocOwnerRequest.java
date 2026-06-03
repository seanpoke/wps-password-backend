package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "获取文档所有者请求")
public class DocOwnerRequest {
    @Schema(description = "文档ID", required = true, example = "doc123456")
    private String docId;

    @Schema(description = "文件名", required = false, example = "test.docx")
    private String fileName;
}
