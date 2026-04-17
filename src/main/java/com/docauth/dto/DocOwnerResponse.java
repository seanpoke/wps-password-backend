package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "文档所有者信息响应")
public class DocOwnerResponse {
    @Schema(description = "账号", example = "zhangsan")
    private String ownerAccount;
    
    @Schema(description = "姓名", example = "张三")
    private String ownerName;
    
    @Schema(description = "当前用户是否有读权限", example = "true")
    private Boolean readAuth;
    
    @Schema(description = "当前用户是否有写权限", example = "true")
    private Boolean writeAuth;
}
