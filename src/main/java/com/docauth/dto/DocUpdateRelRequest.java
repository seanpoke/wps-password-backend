package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "更新文档授权请求")
public class DocUpdateRelRequest {
    @Schema(description = "文档ID", example = "doc123456", requiredMode = Schema.RequiredMode.REQUIRED)
    private String docId;
    
    @Schema(description = "用户DN列表", example = "[\"cn=张三,ou=技术部,dc=example,dc=com\"]")
    private List<String> accountDnList;
    
    @Schema(description = "部门DN列表", example = "[\"ou=技术部,dc=example,dc=com\"]")
    private List<String> deptDnList;
}