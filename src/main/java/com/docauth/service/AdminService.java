package com.docauth.service;

import com.docauth.dto.DocAuthVo;
import com.docauth.dto.DocDetailVo;
import com.docauth.dto.DeptRefVo;
import com.docauth.dto.DocPageVo;
import com.docauth.dto.DeptVisibleRef;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.dto.PageResult;
import com.docauth.dto.RoleListVo;
import com.docauth.dto.UserPageVo;
import com.docauth.enums.UserSource;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocShareRel;
import com.docauth.entity.SysDept;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.entity.VisibleDeptRel;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysRoleRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.repository.SysUserRoleRepository;
import com.docauth.repository.VisibleDeptRelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台管理服务：部门 / 外部用户 / 角色 / 可见权限组 / 用户与角色-组绑定 的 CRUD，
 * 以及 LDAP 树勾选、账号搜索等辅助能力（仅限 admin 角色调用，由 Controller 校验）
 */
@Slf4j
@Service
@Transactional
public class AdminService {

    @Autowired
    private SysDeptRepository sysDeptRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    @Autowired
    private DocShareRelRepository docShareRelRepository;

    @Autowired
    private DocInfoRepository docInfoRepository;

    @Autowired
    private VisibleDeptRelRepository visibleDeptRelRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LdapService ldapService;

    private static final String REL_USER = "USER";
    private static final String REL_ROLE = "ROLE";

    /* ===================== 部门 ===================== */

    public List<SysDept> listDepts() {
        return sysDeptRepository.findAll();
    }

    public SysDept createDept(String name, Long parentId) {
        SysDept parent = parentId == null ? null : sysDeptRepository.findById(parentId).orElse(null);
        String path = "ou=" + name;
        if (parent != null) {
            path = path + "," + parent.getPath();
        }
        SysDept d = new SysDept();
        d.setName(name);
        d.setParentId(parentId);
        d.setPath(path);
        d.setSource("LOCAL");
        return sysDeptRepository.save(d);
    }

    public SysDept updateDept(Long id, String name) {
        SysDept d = sysDeptRepository.findById(id).orElseThrow(() -> new RuntimeException("部门不存在"));
        String oldPath = d.getPath();
        String newPath = "ou=" + name;
        if (d.getParentId() != null) {
            SysDept p = sysDeptRepository.findById(d.getParentId()).orElse(null);
            if (p != null) {
                newPath = newPath + "," + p.getPath();
            }
        }
        d.setName(name);
        d.setPath(newPath);
        sysDeptRepository.save(d);
        if (oldPath != null && !oldPath.equals(newPath)) {
            // 子孙部门 DN 形如 ou=child,ou=parent,... 即以 oldPath 为后缀，按后缀替换同步
            for (SysDept child : sysDeptRepository.findAll()) {
                if (child.getId().equals(id)) continue;
                String cp = child.getPath();
                if (cp != null && cp.length() > oldPath.length() && cp.endsWith(oldPath)) {
                    child.setPath(cp.substring(0, cp.length() - oldPath.length()) + newPath);
                    sysDeptRepository.save(child);
                }
            }
        }
        return d;
    }

    public void deleteDept(Long id) {
        SysDept d = sysDeptRepository.findById(id).orElseThrow(() -> new RuntimeException("部门不存在"));
        if (!"LOCAL".equals(d.getSource()) && d.getSource() != null) {
            throw new RuntimeException("LDAP 部门由同步维护，无法删除");
        }
        // 收集以当前部门 DN 为后缀的整棵子树（含自身）
        List<SysDept> subtree = new ArrayList<>();
        subtree.add(d);
        for (SysDept x : sysDeptRepository.findAll()) {
            String p = x.getPath();
            if (p != null && p.length() > d.getPath().length() && p.endsWith(d.getPath())) {
                subtree.add(x);
            }
        }
        // 子树内任意部门有用户则拦截
        for (SysDept x : subtree) {
            if (!sysUserRepository.findByDeptId(x.getId()).isEmpty()) {
                throw new RuntimeException("该部门或其子部门下存在用户，无法删除");
            }
        }
        // 级联清理：每个部门的权限组范围 + 文档授权（按 deptId；授权置 invalid 保留审计）
        for (SysDept x : subtree) {
            visibleDeptRelRepository.deleteByDeptId(x.getId());
            docShareRelRepository.markInvalidByTargetId(x.getId());
        }
        sysDeptRepository.deleteAll(subtree);
    }

    /* ===================== 外部用户 ===================== */

    public List<SysUser> listUsers() {
        return sysUserRepository.findAll();
    }

    /**
     * 用户管理分页查询（后端分页）。
     * keyword 模糊匹配账号/姓名；source 精确匹配来源（LOCAL/LDAP）；roleId 按角色过滤（子查询）。
     */
    public PageResult<UserPageVo> pageUsers(int page, int size, String keyword, String source, Long roleId) {
        Specification<SysUser> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(cb.like(root.get("account"), like), cb.like(root.get("name"), like)));
            }
            if (source != null && !source.isBlank()) {
                ps.add(cb.equal(root.get("source"), source));
            }
            if (roleId != null) {
                jakarta.persistence.criteria.Subquery<Long> sq = query.subquery(Long.class);
                jakarta.persistence.criteria.Root<SysUserRole> sr = sq.from(SysUserRole.class);
                sq.select(sr.get("userId")).where(cb.equal(sr.get("roleId"), roleId));
                ps.add(root.get("id").in(sq));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<SysUser> p = sysUserRepository.findAll(spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "updateTime")));

        // 部门名称批量映射
        Set<Long> deptIds = new HashSet<>();
        p.getContent().forEach(u -> { if (u.getDeptId() != null) deptIds.add(u.getDeptId()); });
        Map<Long, String> deptNames = new HashMap<>();
        if (!deptIds.isEmpty()) {
            sysDeptRepository.findAllById(deptIds).forEach(d -> deptNames.put(d.getId(), d.getName()));
        }

        List<UserPageVo> rows = new ArrayList<>();
        for (SysUser u : p.getContent()) {
            UserPageVo vo = new UserPageVo();
            vo.setId(u.getId());
            vo.setAccount(u.getAccount());
            vo.setName(u.getName());
            vo.setEmail(u.getEmail());
            vo.setDeptId(u.getDeptId());
            vo.setDeptName(u.getDeptId() == null ? null : deptNames.get(u.getDeptId()));
            vo.setSource(u.getSource());
            vo.setMustChangePwd(u.getMustChangePwd());
            vo.setUpdateTime(u.getUpdateTime());
            List<UserPageVo.RoleItem> roles = new ArrayList<>();
            for (SysUserRole ur : sysUserRoleRepository.findByUserId(u.getId())) {
                sysRoleRepository.findById(ur.getRoleId())
                        .ifPresent(r -> roles.add(new UserPageVo.RoleItem(r.getId(), r.getCode(), r.getName())));
            }
            vo.setRoles(roles);
            rows.add(vo);
        }
        return new PageResult<>(rows, p.getTotalElements(), safePage, safeSize);
    }

    public SysUser createUser(String account, String name, String password, Long deptId, String email, List<Long> visibleDeptIds) {
        if (sysUserRepository.existsByAccount(account)) {
            throw new RuntimeException("账号已存在");
        }
        if (deptId != null && !sysDeptRepository.existsById(deptId)) {
            throw new RuntimeException("部门不存在");
        }
        SysUser u = new SysUser();
        u.setAccount(account);
        u.setName(name);
        u.setEmail(email);
        // 密码留空时使用默认密码：账号@123456，且要求首登改密；填写了密码则不需要改密
        boolean useDefaultPwd = (password == null || password.trim().isEmpty());
        String finalPwd = useDefaultPwd ? account + "@123456" : password;
        u.setPasswordHash(passwordEncoder.encode(finalPwd));
        u.setDeptId(deptId);
        u.setMustChangePwd(useDefaultPwd ? 1 : 0);
        u.setSource("LOCAL");
        SysUser saved = sysUserRepository.save(u);
        // 默认分配 user 角色
        SysRole userRole = sysRoleRepository.findByCode("user").orElse(null);
        if (userRole != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(saved.getId());
            ur.setRoleId(userRole.getId());
            sysUserRoleRepository.save(ur);
        }
        // 可见部门权限（按 USER 绑定）
        saveVisibleDepts(REL_USER, saved.getId(), visibleDeptIds);
        return saved;
    }

    /**
     * 修改用户。
     * LOCAL 来源：姓名/邮箱/部门/角色均可改（账号、来源不可改）。
     * LDAP 来源：仅允许调整角色，其余字段随同步维护。
     */
    public SysUser updateUser(Long id, String name, Long deptId, String email, List<Long> roleIds, List<Long> visibleDeptIds) {
        SysUser u = sysUserRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (UserSource.LDAP.name().equals(u.getSource())) {
            applyRoleIds(u, roleIds);
            // 可见部门权限属于权限范畴，LDAP 用户同样可配置
            saveVisibleDepts(REL_USER, u.getId(), visibleDeptIds);
            return u;
        }
        if (name != null) {
            u.setName(name);
        }
        if (email != null) {
            u.setEmail(email);
        }
        if (deptId != null) {
            u.setDeptId(deptId);
        }
        u = sysUserRepository.save(u);
        applyRoleIds(u, roleIds);
        // 可见部门权限（按 USER 绑定）
        saveVisibleDepts(REL_USER, u.getId(), visibleDeptIds);
        return u;
    }

    /** 将用户角色绑定为 roleIds 集合（差量增删）；roleIds 为 null 时跳过 */
    private void applyRoleIds(SysUser u, List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        List<SysUserRole> current = sysUserRoleRepository.findByUserId(u.getId());
        Set<Long> target = new HashSet<>(roleIds);
        for (SysUserRole ur : current) {
            if (!target.contains(ur.getRoleId())) {
                sysUserRoleRepository.delete(ur);
            }
        }
        Set<Long> currentIds = new HashSet<>();
        for (SysUserRole ur : current) {
            currentIds.add(ur.getRoleId());
        }
        for (Long rid : roleIds) {
            if (currentIds.contains(rid)) {
                continue;
            }
            if (!sysRoleRepository.existsById(rid)) {
                throw new RuntimeException("角色不存在: " + rid);
            }
            SysUserRole ur = new SysUserRole();
            ur.setUserId(u.getId());
            ur.setRoleId(rid);
            sysUserRoleRepository.save(ur);
        }
    }

    /** 全量保存某主体（USER/ROLE）的可见部门权限（先删旧绑定，再逐个插入；deptIds 为 null 时跳过，空列表仅清空） */
    private void saveVisibleDepts(String relType, Long relId, List<Long> deptIds) {
        if (deptIds == null) {
            return;
        }
        visibleDeptRelRepository.deleteByRelTypeAndRelId(relType, relId);
        for (Long deptId : deptIds) {
            if (deptId == null) {
                continue;
            }
            if (deptId == 0L) {
                // 逻辑“全部”节点：deptId=0，不要求真实部门存在
                visibleDeptRelRepository.save(new VisibleDeptRel(relType, relId, 0L));
                continue;
            }
            if (!sysDeptRepository.existsById(deptId)) {
                throw new RuntimeException("部门不存在: " + deptId);
            }
            visibleDeptRelRepository.save(new VisibleDeptRel(relType, relId, deptId));
        }
    }

    public void deleteUser(Long id) {
        SysUser u = sysUserRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        // 级联清理：用户角色 + 用户权限组绑定 + 文档授权(type=1)
        sysUserRoleRepository.deleteByUserId(u.getId());
        visibleDeptRelRepository.deleteByRelTypeAndRelId(REL_USER, u.getId());
        docShareRelRepository.markInvalidByTargetId(u.getId());
        sysUserRepository.delete(u);
    }

    public void resetPassword(Long id, String newPassword) {
        SysUser u = sysUserRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        // 不填新密码则恢复初始密码：账号@123456，且要求首登改密；填写了密码则不需要改密
        boolean useDefaultPwd = (newPassword == null || newPassword.trim().isEmpty());
        String finalPwd = useDefaultPwd ? u.getAccount() + "@123456" : newPassword;
        u.setPasswordHash(passwordEncoder.encode(finalPwd));
        u.setMustChangePwd(useDefaultPwd ? 1 : 0);
        sysUserRepository.save(u);
    }

    public SysUser findUserByAccount(String account) {
        if (account == null || account.isEmpty()) {
            return null;
        }
        return sysUserRepository.findByAccount(account).orElse(null);
    }

    /* ===================== 角色 ===================== */

    public List<SysRole> listRoles() {
        return sysRoleRepository.findAllByOrderByPriorityAsc();
    }

    /** 角色列表（附带可见部门权限名称；deptId=0 显示为“全部”） */
    public List<RoleListVo> listRolesWithVisible() {
        List<SysRole> roles = sysRoleRepository.findAllByOrderByPriorityAsc();
        List<RoleListVo> res = new java.util.ArrayList<>();
        for (SysRole r : roles) {
            RoleListVo vo = new RoleListVo();
            vo.setId(r.getId());
            vo.setCode(r.getCode());
            vo.setName(r.getName());
            vo.setPriority(r.getPriority());
            vo.setRemark(r.getRemark());
            List<String> names = new java.util.ArrayList<>();
            for (VisibleDeptRel rel : visibleDeptRelRepository.findByRelTypeAndRelId(REL_ROLE, r.getId())) {
                if (rel.getDeptId() != null && rel.getDeptId() == 0L) {
                    names.add("全部");
                } else {
                    SysDept d = sysDeptRepository.findById(rel.getDeptId()).orElse(null);
                    if (d != null) names.add(d.getName());
                }
            }
            vo.setVisibleDeptNames(names);
            res.add(vo);
        }
        return res;
    }

    public SysRole createRole(String code, String name, Integer priority, String remark, List<Long> visibleDeptIds) {
        if (code == null || code.isEmpty()) {
            throw new RuntimeException("角色编码不能为空");
        }
        if (sysRoleRepository.existsByCode(code)) {
            throw new RuntimeException("角色编码已存在");
        }
        SysRole r = new SysRole();
        r.setCode(code);
        r.setName(name);
        r.setPriority(priority == null ? 100 : priority);
        r.setRemark(remark);
        SysRole saved = sysRoleRepository.save(r);
        // 可见部门权限（按 ROLE 绑定）
        saveVisibleDepts(REL_ROLE, saved.getId(), visibleDeptIds);
        return saved;
    }

    public SysRole updateRole(Long id, String name, Integer priority, String remark, List<Long> visibleDeptIds) {
        SysRole r = sysRoleRepository.findById(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        if ("admin".equalsIgnoreCase(r.getCode())) {
            throw new RuntimeException("admin 角色不可修改");
        }
        if (name != null) {
            r.setName(name);
        }
        if (priority != null) {
            r.setPriority(priority);
        }
        if (remark != null) {
            r.setRemark(remark);
        }
        r = sysRoleRepository.save(r);
        // 可见部门权限（按 ROLE 绑定，全量覆盖）
        saveVisibleDepts(REL_ROLE, r.getId(), visibleDeptIds);
        return r;
    }

    public void deleteRole(Long id) {
        SysRole r = sysRoleRepository.findById(id).orElse(null);
        if (r == null) {
            throw new RuntimeException("角色不存在");
        }
        if ("admin".equalsIgnoreCase(r.getCode())) {
            throw new RuntimeException("admin 角色不可删除");
        }
        sysUserRoleRepository.deleteByRoleId(id);
        visibleDeptRelRepository.deleteByRelTypeAndRelId(REL_ROLE, id);
        sysRoleRepository.deleteById(id);
    }

    /* ===================== 用户-角色绑定 ===================== */

    public List<SysRole> listUserRoles(String account) {
        SysUser u = sysUserRepository.findByAccount(account).orElse(null);
        if (u == null) {
            return new ArrayList<>();
        }
        List<SysRole> roles = new ArrayList<>();
        for (SysUserRole ur : sysUserRoleRepository.findByUserId(u.getId())) {
            sysRoleRepository.findById(ur.getRoleId()).ifPresent(roles::add);
        }
        return roles;
    }

    public SysUserRole bindUserRole(String account, Long roleId) {
        SysUser u = sysUserRepository.findByAccount(account).orElse(null);
        if (u == null) {
            throw new RuntimeException("用户不存在");
        }
        if (!sysRoleRepository.existsById(roleId)) {
            throw new RuntimeException("角色不存在");
        }
        if (sysUserRoleRepository.findByUserIdAndRoleId(u.getId(), roleId).isPresent()) {
            throw new RuntimeException("已绑定该角色");
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(u.getId());
        ur.setRoleId(roleId);
        return sysUserRoleRepository.save(ur);
    }

    public void unbindUserRole(Long id) {
        sysUserRoleRepository.deleteById(id);
    }

    /* =====================     /* ===================== visible dept scope (no group) ===================== */

    public List<SysDept> listVisibleDepts(String relType, Long relId) {
        List<Long> deptIds = visibleDeptRelRepository.findByRelTypeAndRelId(relType, relId)
                .stream().map(VisibleDeptRel::getDeptId).collect(Collectors.toList());
        return sysDeptRepository.findAllById(deptIds);
    }

    public VisibleDeptRel addVisibleDept(String relType, Long relId, Long deptId) {
        if (!sysDeptRepository.existsById(deptId)) {
            throw new RuntimeException("dept not exist");
        }
        if (visibleDeptRelRepository.findByRelTypeAndRelIdAndDeptId(relType, relId, deptId).isPresent()) {
            throw new RuntimeException("dept already in scope");
        }
        VisibleDeptRel r = new VisibleDeptRel();
        r.setRelType(relType);
        r.setRelId(relId);
        r.setDeptId(deptId);
        return visibleDeptRelRepository.save(r);
    }

    public void deleteVisibleDept(String relType, Long relId, Long deptId) {
        visibleDeptRelRepository.findByRelTypeAndRelIdAndDeptId(relType, relId, deptId)
                .ifPresent(visibleDeptRelRepository::delete);
    }

    public List<DeptVisibleRef> listDeptVisibleRefs(Long deptId) {
        List<DeptVisibleRef> refs = new ArrayList<>();
        for (VisibleDeptRel r : visibleDeptRelRepository.findByDeptId(deptId)) {
            refs.add(new DeptVisibleRef(r.getId(), r.getRelType(), r.getRelId(), resolveRelName(r.getRelType(), r.getRelId())));
        }
        return refs;
    }

    public List<DeptRefVo> getDeptRefs() {
        Map<Long, List<String>> relLabelsMap = new HashMap<>();
        Map<Long, List<String>> userLabelsMap = new HashMap<>();
        Map<Long, List<String>> roleLabelsMap = new HashMap<>();
        for (VisibleDeptRel r : visibleDeptRelRepository.findAll()) {
            String name = resolveRelName(r.getRelType(), r.getRelId());
            relLabelsMap.computeIfAbsent(r.getDeptId(), k -> new ArrayList<>()).add(name);
            if (REL_USER.equals(r.getRelType())) {
                userLabelsMap.computeIfAbsent(r.getDeptId(), k -> new ArrayList<>()).add(name);
            } else {
                roleLabelsMap.computeIfAbsent(r.getDeptId(), k -> new ArrayList<>()).add(name);
            }
        }
        List<DeptRefVo> result = new ArrayList<>();
        for (SysDept d : sysDeptRepository.findAll()) {
            List<String> labels = relLabelsMap.getOrDefault(d.getId(), new ArrayList<>());
            List<String> users = userLabelsMap.getOrDefault(d.getId(), new ArrayList<>());
            List<String> roles = roleLabelsMap.getOrDefault(d.getId(), new ArrayList<>());
            long docAuthCount = docShareRelRepository.countValidByTargetIdAndType(d.getId(), 0);
            result.add(new DeptRefVo(d.getId(), labels, users, roles, docAuthCount));
        }
        return result;
    }

    private String resolveRelName(String relType, Long relId) {
        if (REL_USER.equals(relType)) {
            SysUser u = sysUserRepository.findById(relId).orElse(null);
            return u != null ? u.getAccount() : ("user#" + relId);
        }
        SysRole r = sysRoleRepository.findById(relId).orElse(null);
        return r != null ? r.getName() : ("role#" + relId);
    }

    /* ===================== 文档管理 ===================== */

    /**
     * 文档分页查询（后端分页，按创建时间倒序）。
     * keyword 模糊匹配 uid / 文件名 / 所属账号。
     */
    public PageResult<DocPageVo> pageDocs(int page, int size, String keyword) {
        Specification<DocInfo> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> ps = new ArrayList<>();
            if (keyword != null && !keyword.isBlank()) {
                String like = "%" + keyword.trim() + "%";
                ps.add(cb.or(
                        cb.like(root.get("uid"), like),
                        cb.like(root.get("fileName"), like),
                        cb.like(root.get("account"), like)));
            }
            return cb.and(ps.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<DocInfo> p = docInfoRepository.findAll(spec,
                PageRequest.of(safePage - 1, safeSize, Sort.by(Sort.Direction.DESC, "createTime")));
        List<DocPageVo> list = p.getContent().stream().map(DocPageVo::of).collect(Collectors.toList());
        return new PageResult<>(list, p.getTotalElements(), safePage, safeSize);
    }

    /** 文档详情：基本信息 + 有效授权信息（部门/用户） */
    public DocDetailVo docDetail(Long id) {
        DocInfo d = docInfoRepository.findById(id).orElseThrow(() -> new RuntimeException("文档不存在"));
        List<DocAuthVo> auths = new ArrayList<>();
        for (DocShareRel r : docShareRelRepository.findByUid(d.getUid())) {
            if (r.getInvalid() != null && r.getInvalid() == 1) {
                continue; // 失效授权不展示
            }
            String account = null;
            if (r.getType() != null && r.getType() == 1 && r.getTargetId() != null) {
                account = sysUserRepository.findById(r.getTargetId()).map(SysUser::getAccount).orElse(null);
            }
            auths.add(new DocAuthVo(r.getType(), r.getName(), account));
        }
        return DocDetailVo.of(d, auths);
    }

    /** 删除文档及其全部授权关系 */
    public void deleteDoc(Long id) {
        DocInfo d = docInfoRepository.findById(id).orElseThrow(() -> new RuntimeException("文档不存在"));
        docShareRelRepository.deleteAll(docShareRelRepository.findByUid(d.getUid()));
        docInfoRepository.delete(d);
    }

    /* ===================== LDAP 辅助 ===================== */

    public List<LdapNodeDTO> ldapTree() {
        return ldapService.getLdapTreeWithAuth(null);
    }

    public void refreshLdap() {
        ldapService.forceRefreshLdapCache();
    }

    public List<LdapNodeDTO> searchLdap(String keyword) {
        return ldapService.searchLdapUsers(keyword);
    }
}
