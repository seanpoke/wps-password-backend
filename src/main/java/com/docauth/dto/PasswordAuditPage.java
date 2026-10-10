package com.docauth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "密码审计分页结果")
public class PasswordAuditPage {

    @Schema(description = "当前页记录")
    private List<PasswordAuditRecord> list;

    @Schema(description = "符合条件的总记录数")
    private long total;
}
