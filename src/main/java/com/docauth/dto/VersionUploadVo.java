package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 安装包上传（暂存）结果：文件此时尚未对外发布，也不会被任何版本记录引用。
 * 需在新增/编辑版本「保存」时携带 tempId 才会发布生效；点「取消」调用丢弃接口即可完全回滚。
 */
@Data
@Schema(description = "安装包暂存结果（未发布）")
public class VersionUploadVo {

    @Schema(description = "暂存文件ID：保存版本时放入 tempId 提交；取消时调用删除暂存接口丢弃")
    private String tempId;

    @Schema(description = "原始文件名（仅用于界面展示）", example = "PasswordManager-1.0.5.apk")
    private String fileName;

    @Schema(description = "文件字节数")
    private long size;
}
