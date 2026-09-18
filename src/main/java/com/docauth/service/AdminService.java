package com.docauth.service;

import com.docauth.dto.DeptRefVo;
import com.docauth.dto.DeptVisibleRef;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.SysDept;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.entity.VisibleDeptRel;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysRoleRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.repository.SysUserRoleRepository;
import com.docauth.repository.VisibleDeptRelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    public SysUser createUser(String account, String name, String password, Long deptId) {
        if (sysUserRepository.existsByAccount(account)) {
            throw new RuntimeException("账号已存在");
        }
        if (deptId != null && !sysDeptRepository.existsById(deptId)) {
            throw new RuntimeException("部门不存在");
        }
        SysUser u = new SysUser();
        u.setAccount(account);
        u.setName(name);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setDeptId(deptId);
        u.setStatus(1);
        u.setMustChangePwd(1);
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
        return saved;
    }

    public SysUser updateUser(Long id, String name, Long deptId, Integer status) {
        SysUser u = sysUserRepository.findById(id).orElseThrow(() -> new RuntimeException("用户不存在"));
        if (name != null) {
            u.setName(name);
        }
        if (deptId != null) {
            u.setDeptId(deptId);
        }
        if (status != null) {
            u.setStatus(status);
        }
        return sysUserRepository.save(u);
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
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setMustChangePwd(1);
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

    public SysRole createRole(String code, String name, Integer priority, String remark) {
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
        return sysRoleRepository.save(r);
    }

    public SysRole updateRole(Long id, String name, Integer priority, String remark) {
        SysRole r = sysRoleRepository.findById(id).orElseThrow(() -> new RuntimeException("角色不存在"));
        if (name != null) {
            r.setName(name);
        }
        if (priority != null) {
            r.setPriority(priority);
        }
        if (remark != null) {
            r.setRemark(remark);
        }
        return sysRoleRepository.save(r);
    }

    public void deleteRole(Long id) {
        if (!sysRoleRepository.existsById(id)) {
            throw new RuntimeException("角色不存在");
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
        for (VisibleDeptRel r : visibleDeptRelRepository.findAll()) {
            relLabelsMap.computeIfAbsent(r.getDeptId(), k -> new ArrayList<>())
                    .add(resolveRelName(r.getRelType(), r.getRelId()));
        }
        List<DeptRefVo> result = new ArrayList<>();
        for (SysDept d : sysDeptRepository.findAll()) {
            List<String> labels = relLabelsMap.getOrDefault(d.getId(), new ArrayList<>());
            long docAuthCount = docShareRelRepository.countValidByTargetIdAndType(d.getId(), 0);
            result.add(new DeptRefVo(d.getId(), labels, docAuthCount));
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
