package com.docauth.service;

import com.docauth.dto.SyncApplyRequest;
import com.docauth.dto.SyncDiffNode;
import com.docauth.service.ConfigService;
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
import org.springframework.context.annotation.Lazy;
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
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.time.LocalDateTime;

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

    /** 自注入（经代理），使 persistSync/persistApply 的 @Transactional 在外部非事务方法中生效 */
    @Autowired
    @Lazy
    private LdapSyncService self;

    @Autowired
    private ConfigService configService;

    /** 同步互斥锁：定时 fullSync 与手动 apply 共用，避免重叠写 */
    private final AtomicBoolean syncing = new AtomicBoolean(false);
    /** 上次同步状态（OK / SKIPPED_NO_SUBTREE / FAILED_LDAP_ERROR 等） */
    private volatile String lastStatus;
    /** 上次同步完成时间 */
    private volatile LocalDateTime lastSyncTime;

    private void deleteGone(Map<String, LdapService.LdapTreeNode> deptMap, List<LdapService.LdapTreeNode> userNodes) {
        // 先收集 LDAP 中已消失的部门 / 用户 id，再按 IN 批量清理（一条 SQL 删多行）
        List<Long> goneDeptIds = new ArrayList<>();
        for (SysDept d : sysDeptRepository.findBySource("LDAP")) {
            if (!deptMap.containsKey(d.getPath().toLowerCase())) goneDeptIds.add(d.getId());
        }
        Set<String> ldapAccounts = new HashSet<>();
        for (LdapService.LdapTreeNode n : userNodes) ldapAccounts.add(acc(n).toLowerCase());
        List<Long> goneUserIds = new ArrayList<>();
        for (SysUser u : sysUserRepository.findBySource("LDAP")) {
            if (!ldapAccounts.contains(u.getAccount().toLowerCase())) goneUserIds.add(u.getId());
        }
        if (!goneDeptIds.isEmpty()) {
            docShareRelRepository.markInvalidByTargetIds(goneDeptIds);
            visibleDeptRelRepository.deleteByDeptIds(goneDeptIds);
            sysDeptRepository.deleteByIds(goneDeptIds);
        }
        if (!goneUserIds.isEmpty()) {
            docShareRelRepository.markInvalidByTargetIds(goneUserIds);
            sysUserRoleRepository.deleteByUserIds(goneUserIds);
            visibleDeptRelRepository.deleteByRelTypeAndRelIds("USER", goneUserIds);
            sysUserRepository.deleteByIds(goneUserIds);
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
        Map<Long, String> dbDeptNameById = new HashMap<>();
        for (SysDept d : sysDeptRepository.findBySource("LDAP")) {
            dbDeptId.put(d.getPath().toLowerCase(), d.getId());
            dbDeptName.put(d.getPath().toLowerCase(), d.getName());
            dbDeptParent.put(d.getPath().toLowerCase(), d.getParentId());
            dbDeptNameById.put(d.getId(), d.getName());
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
                String oldName = dbDeptName.get(n.getDn().toLowerCase());
                String oldParentName = dbDeptParent.get(n.getDn().toLowerCase()) != null
                        ? dbDeptNameById.get(dbDeptParent.get(n.getDn().toLowerCase())) : null;
                String newParentName = pd != null ? ldapDeptName(deptByDn.get(pd.toLowerCase())) : null;
                boolean nameSame = Objects.equals(oldName, ldapName);
                boolean parentSame = Objects.equals(dbDeptParent.get(n.getDn().toLowerCase()), expectedParent);
                node.setStatus(nameSame && parentSame ? "SAME" : "CHANGED");
                if (!nameSame || !parentSame) {
                    List<SyncDiffNode.ChangeItem> ch = new ArrayList<>();
                    if (!nameSame) ch.add(new SyncDiffNode.ChangeItem("名称", oldName, ldapName));
                    if (!parentSame) ch.add(new SyncDiffNode.ChangeItem("上级部门", oldParentName, newParentName));
                    node.setChanges(ch);
                }
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
                String oldName = dbUserName.get(a.toLowerCase());
                String oldDeptName = dbUserDept.get(a.toLowerCase()) != null
                        ? dbDeptNameById.get(dbUserDept.get(a.toLowerCase())) : null;
                String newDeptName = pd != null ? ldapDeptName(deptByDn.get(pd.toLowerCase())) : null;
                boolean nameSame = Objects.equals(oldName, ldapName);
                boolean deptSame = Objects.equals(dbUserDept.get(a.toLowerCase()), expectedDept);
                node.setStatus(nameSame && deptSame ? "SAME" : "CHANGED");
                if (!nameSame || !deptSame) {
                    List<SyncDiffNode.ChangeItem> ch = new ArrayList<>();
                    if (!nameSame) ch.add(new SyncDiffNode.ChangeItem("姓名", oldName, ldapName));
                    if (!deptSame) ch.add(new SyncDiffNode.ChangeItem("所属部门", oldDeptName, newDeptName));
                    node.setChanges(ch);
                }
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

    @Autowired
    private OrgTreeCacheService orgTreeCacheService;

    // ============ 确认应用 ============
    public String apply(SyncApplyRequest req) {
        if (!syncing.compareAndSet(false, true)) {
            throw new RuntimeException("已有同步任务进行中，请稍后重试");
        }
        try {
            List<LdapService.LdapTreeNode> all = ldapService.getAllLdapEntries(); // 远程：事务外获取
            String result = self.persistApply(req, all);
            ldapService.forceRefreshLdapCache(); // 远程：事务外刷新 live-LDAP 缓存
            orgTreeCacheService.forceRefresh();  // 同步 sys_dept/sys_user 后刷新组织树缓存（DB-only）
            return result;
        } finally {
            syncing.set(false);
        }
    }

    /**
     * 定时/手动全量同步：
     * <ol>
     *   <li>未配置 subTree → 不执行（SKIPPED_NO_SUBTREE）。</li>
     *   <li>按 subTree 逐根拉取；任一已配置根 LDAP 接口异常 → 跳过整轮（零写入零删除，FAILED_LDAP_ERROR）。</li>
     *   <li>连接正常（含某根返回 0 条）仍继续，以配置范围为唯一真相；事务内 upsert 全部 + deleteGone 清理消失项（含 0 条根下旧数据，2b）。</li>
     *   <li>完成后刷新 live-LDAP 缓存与组织树缓存。</li>
     * </ol>
     * 与 apply 共用 syncing 锁，避免重叠写。
     */
    public Map<String, Object> fullSync() {
        if (!syncing.compareAndSet(false, true)) {
            return statusMap("SKIPPED", "已有同步任务进行中，跳过本次");
        }
        LocalDateTime startTime = LocalDateTime.now();
        try {
            List<String> trees = configService.getLdapTrees();
            if (trees == null || trees.isEmpty()) {
                lastStatus = "SKIPPED_NO_SUBTREE";
                lastSyncTime = startTime;
                return statusMap("SKIPPED", "未配置 subTree，不执行同步");
            }
            Map<String, LdapService.LdapTreeNode> deptByDn = new HashMap<>();
            List<LdapService.LdapTreeNode> userNodes = new ArrayList<>();
            try {
                Map<String, List<LdapService.LdapTreeNode>> byRoot = ldapService.getAllLdapEntriesByRoot();
                for (List<LdapService.LdapTreeNode> entries : byRoot.values()) {
                    for (LdapService.LdapTreeNode n : entries) {
                        if (attr(n, "sAMAccountName") != null) {
                            userNodes.add(n);
                        } else {
                            deptByDn.put(n.getDn().toLowerCase(), n);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[ldapSync] LDAP 拉取异常，跳过整轮同步（不改动数据库）：{}", e.getMessage(), e);
                lastStatus = "FAILED_LDAP_ERROR";
                lastSyncTime = startTime;
                return statusMap("FAILED", "LDAP 接口异常，已跳过本轮同步");
            }
            String result = self.persistFullSync(deptByDn, userNodes);
            ldapService.forceRefreshLdapCache();
            orgTreeCacheService.forceRefresh();
            lastStatus = "OK";
            lastSyncTime = LocalDateTime.now();
            log.info("[ldapSync] 全量同步成功：{}", result);
            return statusMap("OK", result);
        } finally {
            syncing.set(false);
        }
    }

    @Transactional
    public String persistFullSync(Map<String, LdapService.LdapTreeNode> deptByDn,
                                  List<LdapService.LdapTreeNode> userNodes) {
        // 部门按 DN 长度升序（父优先）后再 upsert，保证父部门先建
        List<LdapService.LdapTreeNode> depts = new ArrayList<>(deptByDn.values());
        depts.sort(Comparator.comparingInt(n -> n.getDn() == null ? 0 : n.getDn().length()));
        for (LdapService.LdapTreeNode n : depts) {
            upsertDept(n);
        }
        for (LdapService.LdapTreeNode n : userNodes) {
            upsertUser(n);
        }
        // 以配置范围为唯一真相：LDAP 中已消失（含本次 0 条的根下旧数据）一并清理
        deleteGone(deptByDn, userNodes);
        return String.format("全量同步完成：部门 %d，用户 %d", deptByDn.size(), userNodes.size());
    }

    /** 同步状态（供前端「上次同步」展示与手动触发结果） */
    public Map<String, Object> getStatus() {
        Map<String, Object> m = new HashMap<>();
        m.put("running", syncing.get());
        m.put("lastStatus", lastStatus);
        m.put("lastSyncTime", lastSyncTime);
        return m;
    }

    private Map<String, Object> statusMap(String status, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("status", status);
        m.put("message", message);
        return m;
    }

    @Transactional
    public String persistApply(SyncApplyRequest req, List<LdapService.LdapTreeNode> all) {
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
                if (upsertDept(n) != null) upserted++;
            }
        }
        int removed = 0;
        if (req.getRemoveDeptIds() != null) {
            for (Long id : req.getRemoveDeptIds()) { removeDept(id); removed++; }
        }
        if (req.getRemoveUserIds() != null) {
            for (Long id : req.getRemoveUserIds()) { removeUser(id); removed++; }
        }
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
    private SysDept upsertDept(LdapService.LdapTreeNode n) {
        SysDept d = sysDeptRepository.findByPathAndSource(n.getDn(), "LDAP").orElse(null);
        if (d == null) { d = new SysDept(); }
        // 注意：部门改名/移动（CHANGED）不再清除其文档授权与可见范围。
        // 授权清理仅在该部门真正从 LDAP 消失（GONE）时由 deleteGone/removeDept 负责，
        // 避免一次普通改名/移动静默删除该部门所有文档授权。
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
            u.setPasswordHash(null); // LDAP 用户不本地保存密码，登录统一走 LDAP 校验
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
        List<Long> existing = sysUserRoleRepository.findByUserId(u.getId()).stream()
                .map(SysUserRole::getRoleId).collect(java.util.stream.Collectors.toList());
        // LDAP 员工仅授予「绿网员工(greenet)」角色，不再授予「普通用户(user)」
        SysRole greenet = roles.stream()
                .filter(r -> "greenet".equalsIgnoreCase(r.getCode()))
                .findFirst().orElse(null);
        if (greenet != null && !existing.contains(greenet.getId())) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(u.getId());
            ur.setRoleId(greenet.getId());
            sysUserRoleRepository.save(ur);
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

    /** 取 LDAP 部门节点展示名（与 preview 组装节点同名逻辑一致） */
    private static String ldapDeptName(LdapService.LdapTreeNode n) {
        if (n == null) return null;
        return firstNonNull(attr(n, "name"), attr(n, "ou"), extractCn(n.getDn()));
    }
}
