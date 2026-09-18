package com.docauth.service;

import com.docauth.context.UserContext;
import com.docauth.context.UserContextHolder;
import com.docauth.dto.DocOwnerResponse;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.ConfigSecretKey;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocShareRel;
import com.docauth.entity.SysDept;
import com.docauth.entity.SysUser;
import com.docauth.repository.ConfigSecretKeyRepository;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.repository.SysDeptRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.service.ScopeService;
import com.docauth.util.EccUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文档服务 - 处理文档管理相关业务逻辑
 */
@Slf4j
@Service
public class DocService {

    @Autowired
    private DocInfoRepository docInfoRepository;

    @Autowired
    private DocShareRelRepository docShareRelRepository;

    @Autowired
    private ConfigSecretKeyRepository configSecretKeyRepository;

    @Autowired
    private LdapService ldapService;

    @Autowired
    private PasswordLogWriterService passwordLogWriterService;

    @Autowired
    private ScopeService scopeService;

    @Autowired
    private SysDeptRepository sysDeptRepository;

    @Autowired
    private SysUserRepository sysUserRepository;

    /**
     * 获取文档所有者信息
     *
     * @param docId    文档ID
     * @param fileName 文件名（可选）
     * @return 文档所有者响应对象
     * @throws RuntimeException 业务异常时抛出
     */
    public DocOwnerResponse getDocOwner(String docId, String fileName) {
        log.info("[getDocOwner] 开始处理，docId: {}, fileName: {}", docId, fileName);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            throw new RuntimeException("未授权：无法获取当前用户");
        }

        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(docId);

        if (docInfo == null) {
            // 从ThreadLocal获取当前登录用户信息
            String account = currentAccount;
            String name = UserContextHolder.getCurrentName();

            // 创建新的DocInfo记录（不再存储公私钥）
            docInfo = new DocInfo();
            docInfo.setUid(docId);
            docInfo.setAccount(account);
            docInfo.setName(name != null ? name : account);
            docInfo.setFileName(fileName);
            docInfo.setCreateBy(account);
            docInfoRepository.save(docInfo);

            log.info("[getDocOwner] 创建新文档记录，docId: {}, owner: {}, fileName: {}", docId, account, fileName);


        } else if (fileName != null && !fileName.isEmpty() && docInfo.getFileName() == null) {
            // 如果文档已存在但fileName为空，则更新fileName
            docInfo.setFileName(fileName);
            docInfoRepository.save(docInfo);
            log.info("[getDocOwner] 更新文档fileName，docId: {}, fileName: {}", docId, fileName);
        }

        // 判断当前用户的读写权限
        boolean readAuth = false;
        boolean writeAuth = false;

        // 如果当前用户是文档所有者，则同时拥有读写权限
        if (docInfo.getAccount().equals(currentAccount)) {
            readAuth = true;
            writeAuth = true;
        } else {
            // 否则检查是否有授权权限（只有读权限）
            readAuth = hasUserPermission(docId, currentAccount);
            writeAuth = false;
        }

        // 构建响应
        DocOwnerResponse response = new DocOwnerResponse();
        response.setOwnerAccount(docInfo.getAccount());
        response.setOwnerName(docInfo.getName());
        response.setReadAuth(readAuth);
        response.setWriteAuth(writeAuth);

        return response;
    }

    /**
     * 获取文档密码
     *
     * @param docId         文档ID
     * @param encryPassword ECC加密的密码
     * @param keyVersion    公私钥版本，默认为"default"
     * @param isTemp        是否为临时操作，true则跳过文件存在性和所有者校验
     * @return 解密后的密码响应对象
     * @throws RuntimeException 业务异常时抛出
     */
    public DocPasswordResponse getDocPassword(String docId, String encryPassword, String keyVersion, Boolean isTemp) {
        log.info("[getDocPassword] 开始处理，docId: {}, keyVersion: {}, isTemp: {}", docId, keyVersion, isTemp);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            throw new RuntimeException("未授权：无法获取当前用户");
        }

        // 如果isTemp为true，跳过文件存在性校验和权限校验
        if (isTemp != null && isTemp) {
            log.info("[getDocPassword] isTemp为true，跳过文件存在性和权限校验，docId: {}", docId);

            // 直接根据keyVersion从ConfigSecretKey中获取私钥
            String actualKeyVersion = keyVersion != null && !keyVersion.isEmpty() ? keyVersion : "default";
            ConfigSecretKey configSecretKey = configSecretKeyRepository.findByKeyVersion(actualKeyVersion)
                    .orElseThrow(() -> new RuntimeException("未找到对应的配置密钥，keyVersion: " + actualKeyVersion));

            // 使用ECC私钥解密
            try {
                String password = EccUtil.decrypt(encryPassword, configSecretKey.getPrivateKey());

                // 构建响应
                DocPasswordResponse response = new DocPasswordResponse();
                response.setPassword(password);

                log.info("[getDocPassword] 密码获取成功（临时模式），docId: {}, keyVersion: {}, 用户：{}", docId, actualKeyVersion, currentAccount);
                return response;
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("[getDocPassword] ECC解密失败: {}", e.getMessage(), e);
                throw new RuntimeException("解密失败", e);
            }
        }

        // 正常模式：从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(docId);
        if (docInfo == null) {
            throw new RuntimeException("文件不存在");
        }

        // 第一步：校验当前用户是否为文档所属人
        if (!docInfo.getAccount().equals(currentAccount)) {
            // 如果不是文档所有人，继续执行后续权限判断
            checkUserPermission(docId, currentAccount);
        }

        // 第二步：根据keyVersion从ConfigSecretKey中获取私钥
        String actualKeyVersion = keyVersion != null && !keyVersion.isEmpty() ? keyVersion : "default";
        ConfigSecretKey configSecretKey = configSecretKeyRepository.findByKeyVersion(actualKeyVersion)
                .orElseThrow(() -> new RuntimeException("未找到对应的配置密钥，keyVersion: " + actualKeyVersion));

        // 使用ECC私钥解密
        try {
            String password = EccUtil.decrypt(encryPassword, configSecretKey.getPrivateKey());

            // 构建响应
            DocPasswordResponse response = new DocPasswordResponse();
            response.setPassword(password);

            log.info("[getDocPassword] 密码获取成功，docId: {}, keyVersion: {}, 用户：{}", docId, actualKeyVersion, currentAccount);
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[getDocPassword] ECC解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 检查用户对文档的访问权限
     *
     * @param docId          文档ID
     * @param currentAccount 当前用户账号
     */
    private void checkUserPermission(String docId, String currentAccount) {
        if (!hasUserPermission(docId, currentAccount)) {
            throw new RuntimeException("无访问权限");
        }
    }

    /**
     * 判断用户对文档是否有访问权限（不抛异常，返回boolean）
     *
     * @param docId          文档ID
     * @param currentAccount 当前用户账号
     * @return 是否有权限
     */
    public boolean hasUserPermission(String docId, String currentAccount) {
        SysUser u = sysUserRepository.findByAccount(currentAccount).orElse(null);
        if (u == null) {
            return false;
        }
        Long userId = u.getId();
        Long userDeptId = u.getDeptId();

        List<DocShareRel> rels = docShareRelRepository.findByUid(docId);
        if (rels == null || rels.isEmpty()) {
            return false;
        }

        Set<Long> ancestorDepts = ancestorDeptIds(userDeptId); // 含自身及其所有祖先
        for (DocShareRel rel : rels) {
            if (rel.getInvalid() != null && rel.getInvalid() == 1) {
                continue; // 失效授权忽略
            }
            if (rel.getType() == 1) {
                if (rel.getTargetId() != null && rel.getTargetId().equals(userId)) {
                    return true;
                }
            } else if (rel.getType() == 0) {
                if (rel.getTargetId() != null && ancestorDepts.contains(rel.getTargetId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 收集部门自身及其所有祖先部门的 id 集合（用于部门级授权的子树判定） */
    private Set<Long> ancestorDeptIds(Long deptId) {
        Set<Long> set = new HashSet<>();
        Long cur = deptId;
        while (cur != null) {
            set.add(cur);
            SysDept d = sysDeptRepository.findById(cur).orElse(null);
            cur = d != null ? d.getParentId() : null;
        }
        return set;
    }

    /**
     * 获取LDAP授权树
     *
     * @param docId 文档ID，可选参数
     * @return LDAP节点树列表
     */
    public List<LdapNodeDTO> getAuthTree(String docId) {
        log.info("[getAuthTree] 开始处理，docId: {}", docId);
        UserContext uc = UserContextHolder.getUserContext();
        if (uc == null || uc.getAccount() == null) {
            throw new RuntimeException("未授权：用户未登录");
        }

        // 按当前用户的可见范围过滤组织树（id 化，树由 sys_dept/sys_user 行构建）
        ScopeService.UserScope scope = scopeService.computeScope(uc.getAccount(), uc.getSource());
        Map<String, LdapNodeDTO> byKey = new HashMap<>();
        List<LdapNodeDTO> roots = buildTree(docId, byKey);
        if (scope.isFull()) {
            return roots;
        }
        List<LdapNodeDTO> result = new ArrayList<>();
        for (Long deptId : scope.getRoots()) {
            LdapNodeDTO n = byKey.get("0:" + deptId);
            if (n != null) {
                result.add(n);
            }
        }
        return result;
    }

    /**
     * 更新文档授权
     *
     * @param docId         文档ID
     * @param accountDnList 用户DN列表
     * @param deptDnList    部门DN列表
     * @param isTemp        是否为临时操作，true则跳过文件存在性和所有者校验
     * @throws RuntimeException 业务异常时抛出
     */
    @Transactional
    public void updateDocAuth(String docId, List<Long> userIdList, List<Long> deptIdList, Boolean isTemp) {
        log.info("[updateDocAuth] 开始处理，docId: {}, userIdList size: {}, deptIdList size: {}, isTemp: {}",
                docId,
                userIdList != null ? userIdList.size() : 0,
                deptIdList != null ? deptIdList.size() : 0,
                isTemp);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            throw new RuntimeException("未授权：无法获取当前用户");
        }

        // 如果isTemp为true，跳过文件存在性校验和所有者校验
        if (isTemp != null && isTemp) {
            log.info("[updateDocAuth] isTemp为true，跳过文件存在性和所有者校验，docId: {}", docId);
            applyAuth(docId, userIdList, deptIdList, currentAccount);
            return;
        }

        // 正常模式：从数据库查询文件信息，获取owner
        DocInfo docInfo = docInfoRepository.findByUid(docId);
        if (docInfo == null) {
            throw new RuntimeException("文件不存在");
        }

        // 校验当前用户与owner一致（仅所有者可操作）
        if (!docInfo.getAccount().equals(currentAccount)) {
            throw new RuntimeException("无操作权限，仅文档所有者可更新授权");
        }

        applyAuth(docId, userIdList, deptIdList, currentAccount);
        log.info("[updateDocAuth] 授权更新成功，docId: {}", docId);
    }

    /** 删除旧授权并写入 id 化新授权（部门 type=0 / 用户 type=1） */
    private void applyAuth(String docId, List<Long> userIdList, List<Long> deptIdList, String currentAccount) {
        List<DocShareRel> old = docShareRelRepository.findByUid(docId);
        if (!old.isEmpty()) {
            docShareRelRepository.deleteAll(old);
            log.info("[updateDocAuth] 删除旧授权记录数量: {}", old.size());
        }
        int userCount = 0, deptCount = 0;
        if (deptIdList != null) {
            for (Long id : deptIdList) {
                SysDept d = sysDeptRepository.findById(id).orElse(null);
                if (d == null) continue;
                DocShareRel r = new DocShareRel();
                r.setUid(docId);
                r.setType(0);
                r.setTargetId(id);
                r.setName(d.getName());
                r.setCreateBy(currentAccount);
                docShareRelRepository.save(r);
                deptCount++;
            }
        }
        if (userIdList != null) {
            for (Long id : userIdList) {
                SysUser u = sysUserRepository.findById(id).orElse(null);
                if (u == null) continue;
                DocShareRel r = new DocShareRel();
                r.setUid(docId);
                r.setType(1);
                r.setTargetId(id);
                r.setName(u.getName());
                r.setCreateBy(currentAccount);
                docShareRelRepository.save(r);
                userCount++;
            }
        }
        log.info("[updateDocAuth] 添加部门授权 {} 条，用户授权 {} 条", deptCount, userCount);
    }

    /**
     * 保存操作日志（直接投递到 Logback AsyncAppender，绝对非阻塞）
     *
     * @param docId                文档ID
     * @param path                 文件路径
     * @param keyVersion           密钥版本号（保留但不使用）
     * @param beforePassword       修改前密码（加密字符串，不解密）
     * @param afterPassword        修改后密码（加密字符串，不解密）
     * @param possiblePasswordList 可能的密码集合（加密字符串列表，不解密不排序）
     * @param platform             操作来源平台
     */
    public void saveLog(String docId, String path, String keyVersion, String beforePassword,
                        String afterPassword, List<String> possiblePasswordList, String platform) {
        // 从Token中获取当前登录用户信息（在主线程中捕获）
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            currentAccount = "UNKNOW";
        }

        try {
            // 构建日志消息对象（直接使用原始加密字符串，不解密不排序）
            PasswordLogWriterService.LogMessage message = new PasswordLogWriterService.LogMessage();
            message.setUid(docId);
            message.setPath(path);
            message.setBeforePassword(beforePassword);
            message.setAfterPassword(afterPassword);
            message.setPossiblePasswordList(possiblePasswordList);
            message.setPlatform(platform);
            message.setCreateBy(currentAccount);
            message.setKeyVersion(keyVersion);  // 设置密钥版本号
            // 将消息通过logback异步记录（绝对非阻塞）
            passwordLogWriterService.offerLog(message);
        } catch (Exception e) {
            log.error("[saveLog] 提交日志失败，docId: {}, error: {}", docId, e.getMessage(), e);
            // 异常不影响主流程，只记录日志
        }
    }


    /**
     * 构建部门/用户树（LDAP+本地统一为 sys_dept/sys_user 行），按 docId 打 hasAuth 标（id 化）
     * byKey 暴露 "type:id" -> 节点 映射，便于按 scope 的部门 id 截取子树
     */
    private List<LdapNodeDTO> buildTree(String docId, Map<String, LdapNodeDTO> byKey) {
        List<SysDept> depts = sysDeptRepository.findAll();
        List<DocShareRel> rels = docId != null ? docShareRelRepository.findByUid(docId) : null;

        Map<Long, LdapNodeDTO> deptNodes = new HashMap<>();
        for (SysDept d : depts) {
            LdapNodeDTO n = new LdapNodeDTO();
            n.setId(d.getId());
            n.setType(0);
            n.setName(d.getName());
            n.setAccount(null);
            n.setHasAuth(false);
            deptNodes.put(d.getId(), n);
            byKey.put("0:" + d.getId(), n);
        }

        List<LdapNodeDTO> roots = new ArrayList<>();
        for (SysDept d : depts) {
            LdapNodeDTO n = deptNodes.get(d.getId());
            if (d.getParentId() != null && deptNodes.containsKey(d.getParentId())) {
                addLocalChild(deptNodes.get(d.getParentId()), n);
            } else {
                roots.add(n);
            }
        }

        for (SysUser u : sysUserRepository.findAll()) {
            if (u.getDeptId() == null) {
                continue;
            }
            LdapNodeDTO p = deptNodes.get(u.getDeptId());
            if (p == null) {
                continue;
            }
            LdapNodeDTO un = new LdapNodeDTO();
            un.setId(u.getId());
            un.setType(1);
            un.setName(u.getName());
            un.setAccount(u.getAccount());
            un.setHasAuth(false);
            addLocalEmploy(p, un);
            byKey.put("1:" + u.getId(), un);
        }

        if (rels != null) {
            for (DocShareRel r : rels) {
                if (r.getInvalid() != null && r.getInvalid() == 1) {
                    continue; // 失效授权忽略
                }
                LdapNodeDTO node = byKey.get(r.getType() + ":" + r.getTargetId());
                if (node != null) {
                    node.setHasAuth(true);
                }
            }
        }
        return roots;
    }

    private void addLocalChild(LdapNodeDTO parent, LdapNodeDTO child) {
        if (parent.getDeptList() == null) {
            parent.setDeptList(new ArrayList<>());
        }
        parent.getDeptList().add(child);
    }

    private void addLocalEmploy(LdapNodeDTO parent, LdapNodeDTO child) {
        if (parent.getEmployList() == null) {
            parent.setEmployList(new ArrayList<>());
        }
        parent.getEmployList().add(child);
    }
}
