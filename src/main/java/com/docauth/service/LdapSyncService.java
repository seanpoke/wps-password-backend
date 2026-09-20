package com.docauth.service;

import com.docauth.dto.SyncApplyRequest;
import com.docauth.dto.SyncDiffNode;
import com.docauth.entity.SysDept;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.entity.VisibleDeptRel;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.repository.SysUserRoleRepository;
import com.docauth.repository.VisibleDeptRelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * LDAP 同步服务
 * - sync()：手动全量同步（初始化用，保留旧的删除即清理语义）
 * - preview()/apply()：确认式同步，管理员在对比树勾选后再生效
 * - 仅手动触发（无定时任务）
 */
@Slf4j
@Service
public class LdapSyncService {

    @Autowired
    private LdapService ldapService;
    @Autowired
    private SysDeptRepository sysDeptRepository;
    @Autowired
    private SysUserRepository sysUserRepository;
    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;
    @Autowired
    private DocShareRelRepository docShareRelRepository;
    @Autowired
    private VisibleDeptRelRepository visibleDeptRelRepository;
    @Autowired
    private AdminService adminService;

    // ============ 手动全量同步（初始化） ============
    @Transactional
    public String sync() {
        log.info("[ldapSync] 开始同步 LDAP 部门与用户...");
        List<LdapService.LdapTreeNode> all = ldapService.getAllLdapEntries();
        Map<String, LdapService.LdapTreeNode> deptMap = new HashMap<>();
        List<LdapService.LdapTreeNode> userNodes = new ArrayList<>();
        for (LdapService.LdapTreeNode n : all) {
            if (attr(n, "sAMAccountName") != null) {
                userNodes.add(n);
            } else {
                deptMap.put(n.getDn().toLowerCase(), n);
            }
        }
        int addedDept = 0, addedUser = 0;
        for (LdapService.LdapTreeNode n : deptMap.values()) {
            if (upsertDept(n, false) != null) addedDept++;
        }
        for (LdapService.LdapTreeNode n : userNodes) {
            if (upsertUser(n) != null) addedUser++;
        }
        deleteGone(deptMap, userNodes);
        ldapService.forceRefreshLdapCache();
        String result = String.format("同步完成：部门+%d，用户+%d（已删除 LDAP 中消失的条目）", addedDept, addedUser);
        log.info("[ldapSync] {}", result);
        return result;
    }

    private void deleteGone(Map<String, LdapService.LdapTreeNode> deptMap, List<LdapService.LdapTreeNode> userNodes) {
        for (SysDept d : sysDeptRepository.findBySource("LDAP")) {
            if (!deptMap.containsKey(d.getPath().toLowerCase())) {
                docShareRelRepository.markInvalidByTargetId(d.getId());
                visibleDeptRelRepository.deleteByDeptId(d.getId());
                sysDeptRepository.delete(d);
            }
        }
        for (SysUser u : sysUserRepository.findBySource("LDAP")) {
            boolean exists = false;
            for (LdapService.LdapTreeNode n : userNodes) {
                if (acc(n).equalsIgnoreCase(u.getAccount())) { exists = true; break; }
            }
            if (!exists) {
                docShareRelRepository.markInvalidByTargetId(u.getId());
                sysUserRoleRepository.deleteByUserId(u.getId());
                visibleDeptRelRepository.deleteByRelTypeAndRelId("USER", u.getId());
                sysUserRepository.delete(u);
            }
        }
    }

    // ============ 预览（合并 diff 树） ============
    public List<SyncDiffNode> preview() {
        List<LdapService.LdapTreeNode> all = ldapService.getAllLdapEntries();
        Map<String, LdapService.LdapTreeNode> deptByDn = new HashMap<>();
        List<LdapService.LdapTreeNode> users = new ArrayList<>();
        for (LdapService.LdapTreeNode n : all) {
            if (attr(n, "sAMAccountName") != null) users.add(n);
            else deptByDn.put(n.getDn().toLowerCase(), n);
        }

        Map<String, Long> dbDeptId = new HashMap<>();
        Map<String, String> dbDeptName = new HashMap<>();
        Map<String, Long> dbDeptParent = new HashMap<>();
        for (SysDept d : sysDeptRepository.findBySource("LDAP")) {
            dbDeptId.put(d.getPath().toLowerCase(), d.getId());
            dbDeptName.put(d.getPath().toLowerCase(), d.getName());
            dbDeptParent.put(d.getPath().toLowerCase(), d.getParentId());
        }
        Map<String, Long> dbUserId = new HashMap<>();
        Map<String, String> dbUserName = new HashMap<>();
        Map<String, Long> dbUserDept = new HashMap<>();
        for (SysUser u : sysUserRepository.findBySource("LDAP")) {
            dbUserId.put(u.getAccount().toLowerCase(), u.getId());
            dbUserName.put(u.getAccount().toLowerCase(), u.getName());
            dbUserDept.put(u.getAccount().toLowerCase(), u.getDeptId());
        }

        Map<String, SyncDiffNode> allNodes = new HashMap<>();
        for (LdapService.LdapTreeNode n : deptByDn.values()) {
            SyncDiffNode node = new SyncDiffNode();
            node.setDn(n.getDn());
            node.setType(0);
            String ldapName = firstNonNull(attr(n, "name"), attr(n, "ou"), extractCn(n.getDn()));
            Long id = dbDeptId.get(n.getDn().toLowerCase());
            if (id != null) {
                node.setId(id);
                String pd = parentOf(n.getDn());
                Long expectedParent = pd != null ? dbDeptId.get(pd.toLowerCase()) : null;
                boolean nameSame = Objects.equals(dbDeptName.get(n.getDn().toLowerCase()), ldapName);
                boolean parentSame = Objects.equals(dbDeptParent.get(n.getDn().toLowerCase()), expectedParent);
                node.setStatus(nameSame && parentSame ? "SAME" : "CHANGED");
            } else {
                node.setStatus("NEW");
            }
            node.setName(ldapName);
            allNodes.put(n.getDn().toLowerCase(), node);
        }
        for (LdapService.LdapTreeNode n : users) {
            String a = acc(n);
            SyncDiffNode node = new SyncDiffNode();
            node.setDn(n.getDn());
            node.setType(1);
            node.setAccount(a);
            String ldapName = firstNonNull(attr(n, "cn"), attr(n, "name"), a);
            Long id = dbUserId.get(a.toLowerCase());
            if (id != null) {
                node.setId(id);
                String pd = parentOf(n.getDn());
                Long expectedDept = pd != null ? dbDeptId.get(pd.toLowerCase()) : null;
                boolean nameSame = Objects.equals(dbUserName.get(a.toLowerCase()), ldapName);
                boolean deptSame = Objects.equals(dbUserDept.get(a.toLowerCase()), expectedDept);
                node.setStatus(nameSame && deptSame ? "SAME" : "CHANGED");
            } else {
                node.setStatus("NEW");
            }
            node.setName(ldapName);
            allNodes.put(n.getDn().toLowerCase(), node);
        }
        // GONE：DB 有但 LDAP 无
        for (SysDept d : sysDeptRepository.findBySource("LDAP")) {
            if (!deptByDn.containsKey(d.getPath().toLowerCase())) {
                SyncDiffNode node = new SyncDiffNode();
                node.setId(d.getId());
                node.setDn(d.getPath());
                node.setType(0);
                node.setName(d.getName());
                node.setStatus("GONE");
                allNodes.putIfAbsent(d.getPath().toLowerCase(), node);
            }
        }
        for (SysUser u : sysUserRepository.findBySource("LDAP")) {
            boolean still = users.stream().anyMatch(x -> acc(x).equalsIgnoreCase(u.getAccount()));
            if (!still) {
                SyncDiffNode node = new SyncDiffNode();
                node.setId(u.getId());
                node.setDn(u.getAccount());
                node.setType(1);
                node.setAccount(u.getAccount());
                node.setName(u.getName());
                node.setStatus("GONE");
                String key = u.getAccount().toLowerCase();
                allNodes.putIfAbsent(key, node);
            }
        }
        // 组装树
        List<SyncDiffNode> roots = new ArrayList<>();
        for (SyncDiffNode node : allNodes.values()) {
            String parentDn = parentOf(node.getDn());
            SyncDiffNode parent = parentDn != null ? allNodes.get(parentDn.toLowerCase()) : null;
            if (parent != null) {
                if (parent.getChildren() == null) parent.setChildren(new ArrayList<>());
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    // ============ 确认应用 ============
    @Transactional
    public String apply(SyncApplyRequest req) {
        List<LdapService.LdapTreeNode> all = ldapService.getAllLdapEntries();
        Map<String, LdapService.LdapTreeNode> byDn = new HashMap<>();
        for (LdapService.LdapTreeNode n : all) byDn.put(n.getDn().toLowerCase(), n);

        List<String> addDns = req.getAddDns() != null ? req.getAddDns() : new ArrayList<>();
        addDns.sort(Comparator.comparingInt(String::length)); // 父部门优先
        int upserted = 0;
        for (String dn : addDns) {
            LdapService.LdapTreeNode n = byDn.get(dn.toLowerCase());
            if (n == null) continue;
            if (attr(n, "sAMAccountName") != null) {
                if (upsertUser(n) != null) upserted++;
            } else {
                if (upsertDept(n, true) != null) upserted++;
            }
        }
        int removed = 0;
        if (req.getRemoveDeptIds() != null) {
            for (Long id : req.getRemoveDeptIds()) { removeDept(id); removed++; }
        }
        if (req.getRemoveUserIds() != null) {
            for (Long id : req.getRemoveUserIds()) { removeUser(id); removed++; }
        }
        ldapService.forceRefreshLdapCache();
        return String.format("同步确认完成：新增/更新 %d 条，移除 %d 条", upserted, removed);
    }

    private void removeDept(Long id) {
        SysDept d = sysDeptRepository.findById(id).orElse(null);
        if (d == null) return;
        // 级联清理：文档授权(部门) + 部门权限组范围
        docShareRelRepository.deleteByTargetIdAndType(id, 0);
        visibleDeptRelRepository.deleteByDeptId(id);
        sysDeptRepository.delete(d);
        log.info("[ldapSync] 移除 LDAP 已消失部门 id={} name={}", id, d.getName());
    }

    private void removeUser(Long id) {
        SysUser u = sysUserRepository.findById(id).orElse(null);
        if (u == null) return;
        // 级联清理：文档授权(用户) + 用户角色 + 用户权限组
        docShareRelRepository.deleteByTargetIdAndType(id, 1);
        sysUserRoleRepository.deleteByUserId(id);
        visibleDeptRelRepository.deleteByRelTypeAndRelId("USER", id);
        sysUserRepository.delete(u);
        log.info("[ldapSync] 移除 LDAP 已消失用户 id={} account={}", id, u.getAccount());
    }

    // ============ 工具 ============
    private SysDept upsertDept(LdapService.LdapTreeNode n, boolean clearStaleOnUpdate) {
        SysDept d = sysDeptRepository.findByPathAndSource(n.getDn(), "LDAP").orElse(null);
        if (d == null) { d = new SysDept(); }
        else if (clearStaleOnUpdate) {
            // 更新（部门移动/改名）：清除陈旧授权与权限组范围，由管理员重新授权
            visibleDeptRelRepository.deleteByDeptId(d.getId());
            docShareRelRepository.deleteByTargetIdAndType(d.getId(), 0);
        }
        d.setName(firstNonNull(attr(n, "name"), attr(n, "ou"), extractCn(n.getDn())));
        d.setPath(n.getDn());
        d.setSource("LDAP");
        String pd = parentOf(n.getDn());
        Long pid = pd != null ? sysDeptRepository.findByPathAndSource(pd, "LDAP").map(SysDept::getId).orElse(null) : null;
        d.setParentId(pid);
        return sysDeptRepository.save(d);
    }

    private SysUser upsertUser(LdapService.LdapTreeNode n) {
        String a = acc(n);
        SysUser u = sysUserRepository.findByAccount(a).orElse(null);
        if (u == null) { u = new SysUser(); }
        u.setAccount(a);
        u.setName(firstNonNull(attr(n, "cn"), attr(n, "name"), a));
        u.setSource("LDAP");
        String pd = parentOf(n.getDn());
        Long did = pd != null ? sysDeptRepository.findByPathAndSource(pd, "LDAP").map(SysDept::getId).orElse(null) : null;
        u.setDeptId(did);
        if (u.getId() == null) {
            u.setPasswordHash("LDAP_NO_LOCAL_PASSWORD");
            u.setMustChangePwd(0);
            sysUserRepository.save(u);
            assignDefaultRole(u);
        } else {
            sysUserRepository.save(u);
        }
        return u;
    }

    private void assignDefaultRole(SysUser u) {
        List<SysRole> roles = adminService.listRoles();
        // 默认授予：普通用户(user) + 绿网员工(greenet)
        List<Long> existing = sysUserRoleRepository.findByUserId(u.getId()).stream()
                .map(SysUserRole::getRoleId).collect(java.util.stream.Collectors.toList());
        for (String code : new String[]{"user", "greenet"}) {
            SysRole role = roles.stream()
                    .filter(r -> code.equalsIgnoreCase(r.getCode()))
                    .findFirst().orElse(null);
            if (role != null && !existing.contains(role.getId())) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(u.getId());
                ur.setRoleId(role.getId());
                sysUserRoleRepository.save(ur);
            }
        }
    }

    private static String attr(LdapService.LdapTreeNode n, String key) {
        Map<String, Object> attrs = n.getAttributes();
        if (attrs == null) return null;
        Object v = attrs.get(key);
        if (v == null) return null;
        if (v instanceof List) {
            List<?> list = (List<?>) v;
            return list.isEmpty() ? null : list.get(0).toString();
        }
        return v.toString();
    }

    private static String acc(LdapService.LdapTreeNode n) {
        return attr(n, "sAMAccountName");
    }

    private static String firstNonNull(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isEmpty()) return v;
        }
        return null;
    }

    private static String extractCn(String dn) {
        if (dn == null || dn.isEmpty()) return dn;
        try {
            LdapName ln = new LdapName(dn);
            for (int i = ln.size() - 1; i >= 0; i--) {
                Rdn rdn = ln.getRdn(i);
                if ("CN".equalsIgnoreCase(rdn.getType())) return rdn.getValue().toString();
            }
            return ln.getRdn(ln.size() - 1).getValue().toString();
        } catch (Exception e) {
            return dn;
        }
    }

    private static String parentOf(String dn) {
        if (dn == null || dn.isEmpty()) return null;
        int idx = dn.indexOf(',');
        return idx < 0 ? null : dn.substring(idx + 1);
    }
}
