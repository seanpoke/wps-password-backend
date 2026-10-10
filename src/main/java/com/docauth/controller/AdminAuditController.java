package com.docauth.controller;

import com.docauth.context.UserContext;
import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.PasswordAuditPage;
import com.docauth.dto.PasswordAuditQueryRequest;
import com.docauth.exception.ApiException;
import com.docauth.service.PasswordAuditQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/admin/audit")
@Tag(name = "密码审计查询", description = "按 uid/路径/用户 查询密码变更审计日志(读文件, 不入库)")
public class AdminAuditController {

    @Autowired
    private PasswordAuditQueryService queryService;

    /** 仅允许 admin 角色调用（与 ConfigController.assertAdmin 同语义） */
    private void assertAdmin() {
        UserContext uc = UserContextHolder.getUserContext();
        if (uc == null) {
            throw new ApiException(401, "未登录或登录已过期");
        }
        if (!"admin".equals(uc.getRole())) {
            throw new ApiException(403, "无权限：需要管理员角色");
        }
    }

    @PostMapping("/query")
    @Operation(summary = "查询密码审计日志", description = "按 uid/路径/用户/平台/时间 过滤, 返回解析后的记录(实时读文件, 不落库)。UID 与起止日期为必填, 仅扫描所选日期范围内的日志文件")
    public ApiResponse<PasswordAuditPage> query(@RequestBody PasswordAuditQueryRequest req) {
        assertAdmin();
        if (req.getUid() == null || req.getUid().trim().isEmpty()) {
            throw new ApiException(400, "UID 为必填项");
        }
        if (req.getStartTime() == null || req.getStartTime().trim().isEmpty()
                || req.getEndTime() == null || req.getEndTime().trim().isEmpty()) {
            throw new ApiException(400, "时间范围(开始/结束日期)为必填项");
        }
        return ApiResponse.success(queryService.query(req));
    }
}
