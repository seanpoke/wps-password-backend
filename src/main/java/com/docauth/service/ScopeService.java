package com.docauth.service;

import com.docauth.entity.SysDept;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.entity.VisibleDeptRel;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysRoleRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.repository.SysUserRoleRepository;
import com.docauth.repository.VisibleDeptRelRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 可见范围（Data Scope）服务（id 化：范围统一用 sys_dept.id，不再依赖 DN）
 * 用户可见范围 = 角色绑定的可见部门 ∪ 用户绑定的可见部门
 * （多角色取并集；admin 全量；自身部门不再默认可见，纯显式 RBAC）
 */
@Slf4j
@Service
public class ScopeService {

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysDeptRepository sysDeptRepository;

    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Autowired
    private VisibleDeptRelRepository visibleDeptRelRepository;

    /** 用户可见范围 */
    @Data
    public static class UserScope {
        private boolean full; // admin 全量
        private List<Long> roots = new ArrayList<>(); // 可见根部门 id（含自身及其子树）
    }

    public UserScope computeScope(String account, String source) {
        UserScope scope = new UserScope();

        SysUser u = sysUserRepository.findByAccount(account).orElse(null);
        Long userId = u != null ? u.getId() : null;

        Set<Long> roleIds = new HashSet<>();
        boolean isAdmin = false;
        if (userId != null) {
            for (SysUserRole ur : sysUserRoleRepository.findByUserId(userId)) {
                SysRole r = sysRoleRepository.findById(ur.getRoleId()).orElse(null);
                if (r != null) {
                    roleIds.add(r.getId());
                    if ("admin".equalsIgnoreCase(r.getCode())) {
                        isAdmin = true;
                    }
                }
            }
        }
        if (isAdmin) {
            scope.full = true;
            return scope;
        }

        // user directly bound visible depts（deptId=0 表示“全部”，直接全量）
        if (userId != null) {
            for (VisibleDeptRel r : visibleDeptRelRepository.findByRelTypeAndRelId("USER", userId)) {
                if (r.getDeptId() != null && r.getDeptId() == 0L) {
                    scope.full = true;
                    return scope;
                }
                addRoot(scope, r.getDeptId());
            }
        }
        // depts bound by user's roles（deptId=0 表示“全部”，直接全量）
        for (Long roleId : roleIds) {
            for (VisibleDeptRel r : visibleDeptRelRepository.findByRelTypeAndRelId("ROLE", roleId)) {
                if (r.getDeptId() != null && r.getDeptId() == 0L) {
                    scope.full = true;
                    return scope;
                }
                addRoot(scope, r.getDeptId());
            }
        }
        return scope;
    }

    private void addRoot(UserScope scope, Long deptId) {
        if (deptId == null) {
            return;
        }
        scope.roots.add(deptId);
    }
}
