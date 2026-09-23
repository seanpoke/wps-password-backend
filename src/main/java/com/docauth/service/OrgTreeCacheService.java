package com.docauth.service;

import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.SysDept;
import com.docauth.entity.SysUser;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 组织树缓存服务（单实例：进程内原子引用）。
 *
 * <p>两套缓存，均从 DB（sys_dept / sys_user）物化，绝不连 LDAP：
 * <ul>
 *   <li>{@code deptBySourceRef}：按来源分组的部门扁平列表（仅部门）。
 *       LDAP 部门供“部门管理页”从缓存读取；LOCAL 部门由调用方按需实时查 DB。</li>
 *   <li>{@code permissionTreeRef}：完整权限树（部门+用户，LDAP+LOCAL），不含 hasAuth。
 *       供 /auth/tree 使用，hasAuth 与 scope 由调用方每请求现算。</li>
 * </ul>
 *
 * <p>刷新策略：启动 prewarm + 定时 15 分钟重建 + 手动 forceRefresh（按钮 / LDAP 同步后内部触发）。
 * 重建为“先建临时结构 → 原子替换”，失败保留旧缓存，杜绝 clear 引起的空白窗口与惊群。
 */
@Slf4j
@Service
public class OrgTreeCacheService {

    private final SysDeptRepository sysDeptRepository;
    private final SysUserRepository sysUserRepository;

    /** 按来源分组的部门扁平列表（LDAP / LOCAL），供部门管理页读取。 */
    private final AtomicReference<Map<String, List<SysDept>>> deptBySourceRef =
            new AtomicReference<>(Collections.emptyMap());

    /** 完整权限树（部门+用户），不含 hasAuth，供 /auth/tree 使用。 */
    private final AtomicReference<List<LdapNodeDTO>> permissionTreeRef =
            new AtomicReference<>(Collections.emptyList());

    /** LDAP 部门树（仅 LDAP 来源，DB 物化），供部门管理页「LDAP 部门」页签读取，绝不连 LDAP。 */
    private final AtomicReference<List<LdapNodeDTO>> ldapDeptTreeRef =
            new AtomicReference<>(Collections.emptyList());

    public OrgTreeCacheService(SysDeptRepository sysDeptRepository, SysUserRepository sysUserRepository) {
        this.sysDeptRepository = sysDeptRepository;
        this.sysUserRepository = sysUserRepository;
    }

    @PostConstruct
    public void init() {
        rebuild();
    }

    @Scheduled(fixedDelay = 15, initialDelay = 15, timeUnit = TimeUnit.MINUTES)
    public void scheduledRefresh() {
        rebuild();
    }

    /** 主动刷新（DB-only，不连 LDAP）：供“刷新缓存”按钮与 LDAP 同步 apply 后内部调用。 */
    public void forceRefresh() {
        rebuild();
    }

    /** LDAP 部门列表（缓存快照，只读）。 */
    public List<SysDept> getLdapDepts() {
        List<SysDept> ldap = deptBySourceRef.get().get("LDAP");
        return ldap == null ? Collections.emptyList() : ldap;
    }

    /** 完整权限树（缓存快照，只读；调用方须深拷贝后再打 hasAuth）。 */
    public List<LdapNodeDTO> getPermissionTree() {
        return permissionTreeRef.get();
    }

    /** LDAP 部门树（缓存快照，只读；仅 LDAP 来源，部门管理页「LDAP 部门」页签使用，绝不连 LDAP）。 */
    public List<LdapNodeDTO> getLdapTree() {
        return ldapDeptTreeRef.get();
    }

    /** 重建：临时结构 -> 原子替换；异常保留旧缓存。 */
    private synchronized void rebuild() {
        try {
            List<SysDept> depts = sysDeptRepository.findAll();
            List<SysUser> users = sysUserRepository.findAll();

            Map<String, List<SysDept>> bySource = depts.stream()
                    .collect(Collectors.groupingBy(d -> d.getSource() == null ? "LOCAL" : d.getSource()));

            List<LdapNodeDTO> tree = buildPermissionTree(depts, users);

            // LDAP 部门树：仅 LDAP 来源，供部门管理页「LDAP 部门」页签（绝不连 LDAP）
            List<SysDept> ldapDepts = depts.stream()
                    .filter(d -> "LDAP".equals(d.getSource()))
                    .collect(Collectors.toList());
            List<SysUser> ldapUsers = users.stream()
                    .filter(u -> "LDAP".equals(u.getSource()))
                    .collect(Collectors.toList());
            List<LdapNodeDTO> ldapTree = buildPermissionTree(ldapDepts, ldapUsers);

            deptBySourceRef.set(bySource);   // 原子替换
            permissionTreeRef.set(tree);     // 原子替换
            ldapDeptTreeRef.set(ldapTree);    // 原子替换
            log.info("[OrgTreeCache] 重建完成：部门 {} 个，用户 {} 个；LDAP 部门树 {} 个",
                    depts.size(), users.size(), ldapDepts.size());
        } catch (Exception e) {
            log.error("[OrgTreeCache] 重建失败，保留旧缓存", e);
        }
    }

    /** 构建完整权限树（部门+用户），结构与旧 DocService.buildTree 一致，但不打 hasAuth、不做 scope 过滤。 */
    private List<LdapNodeDTO> buildPermissionTree(List<SysDept> depts, List<SysUser> users) {
        Map<Long, SysDept> deptById = depts.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d, (a, b) -> a));
        Map<Long, LdapNodeDTO> deptNodes = new HashMap<>();
        for (SysDept d : depts) {
            LdapNodeDTO n = new LdapNodeDTO();
            n.setId(d.getId());
            n.setType(0);
            n.setName(d.getName());
            n.setAccount(null);
            n.setDn(d.getPath());   // 部门 DN 取 sys_dept.path
            n.setHasAuth(false);
            deptNodes.put(d.getId(), n);
        }
        List<LdapNodeDTO> roots = new ArrayList<>();
        for (SysDept d : depts) {
            LdapNodeDTO n = deptNodes.get(d.getId());
            if (d.getParentId() != null && deptNodes.containsKey(d.getParentId())) {
                addChild(deptNodes.get(d.getParentId()), n);
            } else {
                roots.add(n);
            }
        }
        for (SysUser u : users) {
            if (u.getDeptId() == null) {
                continue;
            }
            LdapNodeDTO p = deptNodes.get(u.getDeptId());
            if (p == null) {
                continue;
            }
            // 用户 DN 由 CN=账号 + 所属部门 DN 拼接（sys_user 不持久化 DN）
            String userDn = null;
            SysDept ud = deptById.get(u.getDeptId());
            if (ud != null && ud.getPath() != null && u.getAccount() != null) {
                userDn = "CN=" + u.getAccount() + "," + ud.getPath();
            }
            LdapNodeDTO un = new LdapNodeDTO();
            un.setId(u.getId());
            un.setType(1);
            un.setName(u.getName());
            un.setAccount(u.getAccount());
            un.setDn(userDn);
            un.setHasAuth(false);
            addEmploy(p, un);
        }
        return roots;
    }

    private void addChild(LdapNodeDTO parent, LdapNodeDTO child) {
        if (parent.getDeptList() == null) {
            parent.setDeptList(new ArrayList<>());
        }
        parent.getDeptList().add(child);
    }

    private void addEmploy(LdapNodeDTO parent, LdapNodeDTO child) {
        if (parent.getEmployList() == null) {
            parent.setEmployList(new ArrayList<>());
        }
        parent.getEmployList().add(child);
    }
}
