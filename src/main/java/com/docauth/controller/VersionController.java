package com.docauth.controller;

import com.docauth.dto.ApiResponse;
import com.docauth.dto.AppVersionDto;
import com.docauth.dto.VersionCheckResponse;
import com.docauth.dto.VersionPageVo;
import com.docauth.service.AppVersionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 客户端版本管理接口
 */
@Slf4j
@RestController
@RequestMapping("/config/version")
@Tag(name = "客户端版本管理", description = "客户端版本检查与配置接口")
public class VersionController {

    @Autowired
    private AppVersionService appVersionService;

    /** 仅允许 admin 角色调用（与 ConfigController.assertAdmin 同语义） */
    private void assertAdmin() {
        com.docauth.context.UserContext uc = com.docauth.context.UserContextHolder.getUserContext();
        if (uc == null) {
            throw new com.docauth.exception.ApiException(401, "未登录或登录已过期");
        }
        if (!"admin".equals(uc.getRole())) {
            throw new com.docauth.exception.ApiException(403, "无权限：需要管理员角色");
        }
    }

    /**
     * 客户端启动版本检查（无需 token）
     */
    @GetMapping("/check")
    @Operation(summary = "客户端版本检查", description = "按 platform + 当前版本检查是否需要更新（免 token）")
    public ApiResponse<VersionCheckResponse> check(
            @RequestParam String platform,
            @RequestParam String current) {
        log.info("[version/check] platform={}, current={}", platform, current);
        try {
            return ApiResponse.success(appVersionService.checkVersion(platform, current));
        } catch (RuntimeException e) {
            log.warn("[version/check] 校验失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/check] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "版本检查失败：" + e.getMessage());
        }
    }

    /**
     * 获取版本列表（admin，分页）：platform 可选（不传查全平台）；
     * status 可选筛选派生状态；返回分页数据 + 状态汇总（汇总不受筛选影响）
     */
    @GetMapping
    @Operation(summary = "获取版本列表（分页）",
            description = "管理员分页查看版本；status 可选 latest/lowest/available/expired/unreleased/all")
    public ApiResponse<VersionPageVo> list(
            @RequestParam(required = false) String platform,
            @RequestParam(required = false, defaultValue = "all") String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {
        assertAdmin();
        return ApiResponse.success(appVersionService.pageVersions(platform, status, page, size));
    }

    /**
     * 上传版本安装包（admin）：落盘到版本目录，返回可填入 downloadUrl 的相对路径 /downloads/{platform}-{version}{ext}
     */
    @PostMapping("/upload")
    @Operation(summary = "上传版本安装包",
            description = "admin 上传安装包到版本目录，返回相对下载路径（如 /downloads/win-1.0.2.exe）")
    public ApiResponse<String> upload(
            @RequestParam String platform,
            @RequestParam String version,
            @RequestParam MultipartFile file) {
        assertAdmin();
        try {
            return ApiResponse.success(appVersionService.storePackage(platform, version, file));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/upload] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "上传失败：" + e.getMessage());
        }
    }

    /**
     * 新增版本记录（admin）
     */
    @PostMapping
    @Operation(summary = "新增版本", description = "在某个平台下新增一个版本记录")
    public ApiResponse<String> create(@RequestBody AppVersionDto dto) {
        assertAdmin();
        try {
            appVersionService.createVersion(dto);
            return ApiResponse.success("版本新增成功");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/create] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "版本新增失败：" + e.getMessage());
        }
    }

    /**
     * 更新版本元数据（admin）：版本号不可改
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新版本元数据", description = "修改版本下载地址/说明/发布时间（版本号不可改）")
    public ApiResponse<String> update(@PathVariable Long id, @RequestBody AppVersionDto dto) {
        assertAdmin();
        try {
            appVersionService.updateVersion(id, dto);
            return ApiResponse.success("版本更新成功");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/update] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "版本更新失败：" + e.getMessage());
        }
    }

    /**
     * 设为最低支持版本（admin）
     */
    @PutMapping("/{id}/min")
    @Operation(summary = "设为最低支持版本", description = "将该版本标记为最低支持版本（自动取消同平台旧标记）")
    public ApiResponse<String> setMin(@PathVariable Long id) {
        assertAdmin();
        try {
            appVersionService.setMin(id);
            return ApiResponse.success("已设为最低支持版本");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/setMin] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "操作失败：" + e.getMessage());
        }
    }

    /**
     * 设为最新版本（admin）
     */
    @PutMapping("/{id}/latest")
    @Operation(summary = "设为最新版本", description = "将该版本标记为最新版本（自动取消同平台旧标记）")
    public ApiResponse<String> setLatest(@PathVariable Long id) {
        assertAdmin();
        try {
            appVersionService.setLatest(id);
            return ApiResponse.success("已设为最新版本");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/setLatest] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "操作失败：" + e.getMessage());
        }
    }

    /**
     * 删除版本（admin）：当前最新版本不可删
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除版本", description = "删除一个历史版本（当前最新版本不可删）")
    public ApiResponse<String> delete(@PathVariable Long id) {
        assertAdmin();
        try {
            appVersionService.deleteVersion(id);
            return ApiResponse.success("版本已删除");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[version/delete] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "删除失败：" + e.getMessage());
        }
    }
}
