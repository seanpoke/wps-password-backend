package com.docauth.controller;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.AdminRequests;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.dto.SyncApplyRequest;


import com.docauth.entity.SysDept;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.enums.UserSource;

import com.docauth.service.AdminService;
import com.docauth.service.LdapSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Tag(name = "后台管理", description = "部门 / 外部用户 / 可见权限组 / 用户-组绑定 管理（需 admin 角色）")
@Slf4j
public class AdminController {

    @Autowired
    private AdminService adminService;

    @Autowired
    private LdapSyncService ldapSyncService;

    private void assertAdmin() {
        com.docauth.context.UserContext uc = UserContextHolder.getUserContext();
        if (uc == null) {
            throw new com.docauth.exception.ApiException(401, "未登录或登录已过期");
        }
        if (!"admin".equals(uc.getRole())) {
            throw new com.docauth.exception.ApiException(403, "无权限：需要管理员角色");
        }
    }

    /* ===================== 部门 ===================== */

    @GetMapping("/depts")
    @Operation(summary = "部门列表")
    public ApiResponse<?> listDepts() {
        assertAdmin();
        return ApiResponse.success(adminService.listDepts());
    }

    @PostMapping("/depts")
    @Operation(summary = "新建部门")
    public ApiResponse<?> createDept(@RequestBody AdminRequests.CreateDept req) {
        assertAdmin();
        return ApiResponse.success(adminService.createDept(req.getName(), req.getParentId()));
    }

    @PutMapping("/depts/{id}")
    @Operation(summary = "修改部门名称")
    public ApiResponse<?> updateDept(@PathVariable Long id, @RequestBody AdminRequests.UpdateDept req) {
        assertAdmin();
        return ApiResponse.success(adminService.updateDept(id, req.getName()));
    }

    @DeleteMapping("/depts/{id}")
    @Operation(summary = "删除部门")
    public ApiResponse<?> deleteDept(@PathVariable Long id) {
        assertAdmin();
        try {
            adminService.deleteDept(id);
            return ApiResponse.success("删除成功");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /* ===================== 外部用户 ===================== */

    @GetMapping("/users")
    @Operation(summary = "用户列表（全量，兼容旧调用）")
    public ApiResponse<?> listUsers() {
        assertAdmin();
        return ApiResponse.success(adminService.listUsers());
    }

    @GetMapping("/users/sources")
    @Operation(summary = "用户来源枚举（LOCAL/LDAP，供前端筛选下拉动态获取）")
    public ApiResponse<?> listUserSources() {
        assertAdmin();
        java.util.List<java.util.Map<String, String>> list = new java.util.ArrayList<>();
        for (UserSource s : UserSource.values()) {
            java.util.Map<String, String> m = new java.util.HashMap<>();
            m.put("code", s.getCode());
            m.put("label", s.getLabel());
            list.add(m);
        }
        return ApiResponse.success(list);
    }

    @GetMapping("/users/page")
    @Operation(summary = "用户分页查询（keyword 模糊匹配账号/姓名，source 来源枚举 LOCAL/LDAP，roleId 角色；页码从 1 开始）")
    public ApiResponse<?> pageUsers(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String keyword,
                                    @RequestParam(required = false) String source,
                                    @RequestParam(required = false) Long roleId) {
        assertAdmin();
        if (source != null && !source.isBlank() && !UserSource.isValid(source)) {
            return ApiResponse.error(400, "非法来源: " + source);
        }
        return ApiResponse.success(adminService.pageUsers(page, size, keyword, source, roleId));
    }

    @PostMapping("/users")
    @Operation(summary = "新建本地用户（初始密码，需首登改密）")
    public ApiResponse<?> createUser(@RequestBody AdminRequests.CreateUser req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.createUser(req.getAccount(), req.getName(), req.getPassword(), req.getDeptId(), req.getVisibleDeptIds()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/users/{id}")
    @Operation(summary = "修改用户（LOCAL：姓名/部门/角色；LDAP：仅角色）")
    public ApiResponse<?> updateUser(@PathVariable Long id, @RequestBody AdminRequests.UpdateUser req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.updateUser(id, req.getName(), req.getDeptId(), req.getRoleIds(), req.getVisibleDeptIds()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "删除外部用户")
    public ApiResponse<?> deleteUser(@PathVariable Long id) {
        assertAdmin();
        adminService.deleteUser(id);
        return ApiResponse.success("删除成功");
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "管理员重置密码")
    public ApiResponse<?> resetPassword(@PathVariable Long id, @RequestBody AdminRequests.ResetPassword req) {
        assertAdmin();
        adminService.resetPassword(id, req.getNewPassword());
        return ApiResponse.success("重置成功");
    }

    /* ===================== 可见部门范围（无权限组） ===================== */

    @GetMapping("/visible-depts")
    @Operation(summary = "query visible depts bound to a user/role")
    public ApiResponse<?> listVisibleDepts(@RequestParam String relType, @RequestParam Long relId) {
        assertAdmin();
        return ApiResponse.success(adminService.listVisibleDepts(relType, relId));
    }

    @PostMapping("/visible-depts")
    @Operation(summary = "bind a visible dept to a user/role")
    public ApiResponse<?> addVisibleDept(@RequestBody AdminRequests.BindVisibleDept req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.addVisibleDept(req.getRelType(), req.getRelId(), req.getDeptId()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/visible-depts")
    @Operation(summary = "unbind a visible dept from a user/role")
    public ApiResponse<?> deleteVisibleDept(@RequestParam String relType, @RequestParam Long relId, @RequestParam Long deptId) {
        assertAdmin();
        adminService.deleteVisibleDept(relType, relId, deptId);
        return ApiResponse.success("ok");
    }

    @GetMapping("/dept-refs")
    @Operation(summary = "dept tree badges: refs + doc auth count")
    public ApiResponse<?> deptRefs() {
        assertAdmin();
        return ApiResponse.success(adminService.getDeptRefs());
    }

    @GetMapping("/docs/page")
    @Operation(summary = "文档分页查询（keyword 模糊匹配 uid/文件名/所属账号；页码从 1 开始）")
    public ApiResponse<?> pageDocs(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) String keyword) {
        assertAdmin();
        return ApiResponse.success(adminService.pageDocs(page, size, keyword));
    }

    @GetMapping("/docs/{id}")
    @Operation(summary = "文档详情：基本信息 + 授权信息（部门/用户）")
    public ApiResponse<?> docDetail(@PathVariable Long id) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.docDetail(id));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/docs/{id}")
    @Operation(summary = "删除文档及其授权关系")
    public ApiResponse<?> deleteDoc(@PathVariable Long id) {
        assertAdmin();
        try {
            adminService.deleteDoc(id);
            return ApiResponse.success("ok");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @GetMapping("/depts/{id}/refs")
    @Operation(summary = "reverse lookup: who set this dept visible")
    public ApiResponse<?> listDeptVisibleRefs(@PathVariable Long id) {
        assertAdmin();
        return ApiResponse.success(adminService.listDeptVisibleRefs(id));
    }

    /* ===================== 角色 ===================== */

    @GetMapping("/roles")
    @Operation(summary = "角色列表（按优先级升序，附带可见部门权限）")
    public ApiResponse<?> listRoles() {
        assertAdmin();
        return ApiResponse.success(adminService.listRolesWithVisible());
    }

    @PostMapping("/roles")
    @Operation(summary = "新建角色定义")
    public ApiResponse<?> createRole(@RequestBody AdminRequests.CreateRole req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.createRole(req.getCode(), req.getName(), req.getPriority(), req.getRemark(), req.getVisibleDeptIds()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @PutMapping("/roles/{id}")
    @Operation(summary = "修改角色定义")
    public ApiResponse<?> updateRole(@PathVariable Long id, @RequestBody AdminRequests.UpdateRole req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.updateRole(id, req.getName(), req.getPriority(), req.getRemark(), req.getVisibleDeptIds()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/roles/{id}")
    @Operation(summary = "删除角色定义（级联清理用户/组关联）")
    public ApiResponse<?> deleteRole(@PathVariable Long id) {
        assertAdmin();
        try {
            adminService.deleteRole(id);
            return ApiResponse.success("删除成功");
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    /* ===================== 用户-角色绑定 ===================== */

    @GetMapping("/users/find")
    @Operation(summary = "按账号查询用户是否存在（本地或 LDAP 同步，查不到 data 为 null）")
    public ApiResponse<?> findUser(@RequestParam String account) {
        assertAdmin();
        return ApiResponse.success(adminService.findUserByAccount(account));
    }

    @GetMapping("/user-roles")
    @Operation(summary = "查询用户的角色")
    public ApiResponse<?> listUserRoles(@RequestParam String account) {
        assertAdmin();
        return ApiResponse.success(adminService.listUserRoles(account));
    }

    @PostMapping("/user-roles")
    @Operation(summary = "绑定用户到角色")
    public ApiResponse<?> bindUserRole(@RequestBody AdminRequests.BindUserRole req) {
        assertAdmin();
        try {
            return ApiResponse.success(adminService.bindUserRole(req.getAccount(), req.getRoleId()));
        } catch (RuntimeException e) {
            return ApiResponse.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/user-roles/{id}")
    @Operation(summary = "解绑用户角色")
    public ApiResponse<?> unbindUserRole(@PathVariable Long id) {
        assertAdmin();
        adminService.unbindUserRole(id);
        return ApiResponse.success("解绑成功");
    }

    /* ===================== LDAP 辅助 ===================== */

    @GetMapping("/ldap-tree")
    @Operation(summary = "LDAP 组织树（用于后台勾选部门，读缓存）")
    public ApiResponse<?> ldapTree() {
        assertAdmin();
        return ApiResponse.success(adminService.ldapTree());
    }

    @GetMapping("/ldap-sync/preview")
    @Operation(summary = "LDAP 同步预览：返回 DB 与 LDAP 的合并 diff 树（NEW/GONE/CHANGED/SAME）")
    public ApiResponse<?> ldapSyncPreview() {
        assertAdmin();
        return ApiResponse.success(ldapSyncService.preview());
    }

    @PostMapping("/ldap-sync/apply")
    @Operation(summary = "LDAP 同步确认：按管理员勾选应用（addDns 新增/更新，removeDeptIds/removeUserIds 移除）")
    public ApiResponse<?> ldapSyncApply(@RequestBody SyncApplyRequest req) {
        assertAdmin();
        try {
            return ApiResponse.success(ldapSyncService.apply(req));
        } catch (RuntimeException e) {
            log.error("[ldapSyncApply] 失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "同步确认失败：" + e.getMessage());
        }
    }

    @PostMapping("/ldap-sync/full")
    @Operation(summary = "立即全量同步：按当前 subTree 配置全量拉取 LDAP 并 upsert + 清理消失项（与确认式 apply 互斥）")
    public ApiResponse<?> ldapSyncFull() {
        assertAdmin();
        try {
            return ApiResponse.success(ldapSyncService.fullSync());
        } catch (RuntimeException e) {
            log.error("[ldapSyncFull] 失败: {}", e.getMessage(), e);
            return ApiResponse.error(500, "全量同步失败：" + e.getMessage());
        }
    }

    @GetMapping("/ldap-sync/status")
    @Operation(summary = "查询上次同步状态（running / lastStatus / lastSyncTime）")
    public ApiResponse<?> ldapSyncStatus() {
        assertAdmin();
        return ApiResponse.success(ldapSyncService.getStatus());
    }
}
