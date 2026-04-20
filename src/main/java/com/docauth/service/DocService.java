package com.docauth.service;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.DocOwnerResponse;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocPasswordLog;
import com.docauth.entity.DocShareRel;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocOperateLogRepository;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.util.RsaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

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
    private DocOperateLogRepository docOperateLogRepository;

    @Autowired
    private LdapService ldapService;

    @Autowired
    private ConfigService configService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 获取文档所有者信息
     *
     * @param docId 文档ID
     * @return 文档所有者响应对象
     * @throws RuntimeException 业务异常时抛出
     */
    public DocOwnerResponse getDocOwner(String docId) {
        log.info("[getDocOwner] 开始处理，docId: {}", docId);

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

            // 从数据库读取RSA密钥对
            try {
                String publicKey = configService.getPublicKey();
                String privateKey = configService.getPrivateKey();

                if (publicKey == null || privateKey == null) {
                    throw new RuntimeException("系统配置错误：未找到RSA密钥配置");
                }

                // 创建新的DocInfo记录
                docInfo = new DocInfo();
                docInfo.setUid(docId);
                docInfo.setPublicKey(publicKey);
                docInfo.setPrivateKey(privateKey);
                docInfo.setAccount(account);
                docInfo.setName(name != null ? name : account);
                docInfo.setCreateBy(account);
                docInfoRepository.save(docInfo);
                
                log.info("[getDocOwner] 创建新文档记录，docId: {}, owner: {}", docId, account);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                log.error("[getDocOwner] 创建文档信息失败: {}", e.getMessage(), e);
                throw new RuntimeException("创建文档信息失败", e);
            }
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
     * @param docId          文档ID
     * @param encryPassword  RSA加密的密码
     * @return 解密后的密码响应对象
     * @throws RuntimeException 业务异常时抛出
     */
    public DocPasswordResponse getDocPassword(String docId, String encryPassword) {
        log.info("[getDocPassword] 开始处理，docId: {}", docId);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            throw new RuntimeException("未授权：无法获取当前用户");
        }

        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(docId);
        if (docInfo == null) {
            throw new RuntimeException("文件不存在");
        }

        // 第一步：校验当前用户是否为文档所属人
        if (!docInfo.getAccount().equals(currentAccount)) {
            // 如果不是文档所有人，继续执行后续权限判断
            checkUserPermission(docId, currentAccount);
        }

        // 使用私钥解密
        try {
            String password = RsaUtil.decrypt(encryPassword, docInfo.getPrivateKey());
            
            // 构建响应
            DocPasswordResponse response = new DocPasswordResponse();
            response.setPassword(password);
            
            log.info("[getDocPassword] 密码获取成功，docId: {}", docId);
            return response;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("[getDocPassword] 解密失败: {}", e.getMessage(), e);
            throw new RuntimeException("解密失败", e);
        }
    }

    /**
     * 检查用户对文档的访问权限
     *
     * @param docId         文档ID
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
     * @param docId         文档ID
     * @param currentAccount 当前用户账号
     * @return 是否有权限
     */
    public boolean hasUserPermission(String docId, String currentAccount) {
        // 第一步：根据docId查询账号权限的dnList集合
        List<DocShareRel> shareRels = docShareRelRepository.findByUid(docId);
        if (shareRels == null || shareRels.isEmpty()) {
            return false;
        }

        // 提取所有授权的DN路径
        List<String> authorizedDnList = new ArrayList<>();
        for (DocShareRel rel : shareRels) {
            if (rel.getDn() != null && !rel.getDn().isEmpty()) {
                authorizedDnList.add(rel.getDn());
            }
        }

        if (authorizedDnList.isEmpty()) {
            return false;
        }

        // 第二步：通过ldap查询当前用户的dn
        String accountDn = ldapService.getUserDn(currentAccount);
        if (accountDn == null || accountDn.isEmpty()) {
            return false;
        }

        // 第三步：判断账号的dn是否属于"集合中某条路径"的子集（或就是该路径本身）
        for (String authorizedDn : authorizedDnList) {
            // 判断accountDn是否是authorizedDn的子路径或相同路径
            if (isDnSubPath(accountDn, authorizedDn)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 获取LDAP授权树
     *
     * @param docId 文档ID，可选参数
     * @return LDAP节点树列表
     */
    public List<LdapNodeDTO> getAuthTree(String docId) {
        log.info("[getAuthTree] 开始处理，docId: {}", docId);
        return ldapService.getLdapTreeWithAuth(docId);
    }

    /**
     * 更新文档授权
     *
     * @param docId         文档ID
     * @param accountDnList 用户DN列表
     * @param deptDnList    部门DN列表
     * @throws RuntimeException 业务异常时抛出
     */
    @Transactional
    public void updateDocAuth(String docId, List<String> accountDnList, List<String> deptDnList) {
        log.info("[updateDocAuth] 开始处理，docId: {}, accountDnList size: {}, deptDnList size: {}",
                docId,
                accountDnList != null ? accountDnList.size() : 0,
                deptDnList != null ? deptDnList.size() : 0);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            throw new RuntimeException("未授权：无法获取当前用户");
        }

        // 从数据库查询文件信息，获取owner
        DocInfo docInfo = docInfoRepository.findByUid(docId);
        if (docInfo == null) {
            throw new RuntimeException("文件不存在");
        }

        // 校验当前用户与owner一致（仅所有者可操作）
        if (!docInfo.getAccount().equals(currentAccount)) {
            throw new RuntimeException("无操作权限，仅文档所有者可更新授权");
        }

        // 删除doc_share_rel表中该docId的所有旧授权记录
        List<DocShareRel> oldRelations = docShareRelRepository.findByUid(docId);
        if (!oldRelations.isEmpty()) {
            docShareRelRepository.deleteAll(oldRelations);
            log.info("[updateDocAuth] 删除旧授权记录数量: {}", oldRelations.size());
        }

        // 遍历accountDnList，插入用户授权记录
        if (accountDnList != null && !accountDnList.isEmpty()) {
            for (String dn : accountDnList) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(docId);
                shareRel.setType(1); // 1表示用户
                String value = extractValueFromDn(dn);
                shareRel.setName(value);
                shareRel.setDn(dn);
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
            log.info("[updateDocAuth] 添加用户授权记录数量: {}", accountDnList.size());
        }

        // 遍历deptDnList，插入部门授权记录
        if (deptDnList != null && !deptDnList.isEmpty()) {
            for (String dn : deptDnList) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(docId);
                shareRel.setType(0); // 0表示部门
                String value = extractValueFromDn(dn);
                shareRel.setName(value);
                shareRel.setDn(dn);
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
            log.info("[updateDocAuth] 添加部门授权记录数量: {}", deptDnList.size());
        }

        log.info("[updateDocAuth] 授权更新成功，docId: {}", docId);
    }

    /**
     * 保存操作日志
     *
     * @param docId           文档ID
     * @param path            文件路径
     * @param beforePassword  修改前密码
     * @param afterPassword   修改后密码
     * @param possiblePasword 可能的密码集合
     * @throws RuntimeException 业务异常时抛出
     */
    public void saveLog(String docId, String path, String beforePassword, 
                       String afterPassword, List<String> possiblePasword) {
        log.info("[saveLog] 开始处理，docId: {}, path: {}", docId, path);

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            currentAccount = "UNKNOW";
        }

        // 创建操作日志对象
        DocPasswordLog passwordLog = new DocPasswordLog();
        passwordLog.setUid(docId);
        passwordLog.setType("save");
        passwordLog.setCreateBy(currentAccount);
        passwordLog.setPath(path);
        passwordLog.setBeforePassword(beforePassword);
        passwordLog.setAfterPassword(afterPassword);

        // 将可能的密码集合转换为JSON字符串存储
        if (possiblePasword != null && !possiblePasword.isEmpty()) {
            try {
                String jsonPassword = objectMapper.writeValueAsString(possiblePasword);
                passwordLog.setPossiblePassword(jsonPassword);
            } catch (Exception e) {
                log.error("[saveLog] 转换密码集合为JSON失败: {}", e.getMessage(), e);
            }
        }

        docOperateLogRepository.save(passwordLog);
        log.info("[saveLog] 日志保存成功，docId: {}", docId);
    }

    /**
     * 判断accountDn是否是authorizedDn的子路径或相同路径
     *
     * @param accountDn    用户的DN路径
     * @param authorizedDn 授权的DN路径
     * @return 是否有权限
     */
    private boolean isDnSubPath(String accountDn, String authorizedDn) {
        if (accountDn == null || authorizedDn == null) {
            return false;
        }

        // 转为小写进行比较（LDAP DN不区分大小写）
        String lowerAccountDn = accountDn.toLowerCase();
        String lowerAuthorizedDn = authorizedDn.toLowerCase();

        // 如果完全相同，则有权限
        if (lowerAccountDn.equals(lowerAuthorizedDn)) {
            return true;
        }

        // 判断accountDn是否以",authorizedDn"结尾（表示是其子路径）
        if (lowerAccountDn.endsWith("," + lowerAuthorizedDn)) {
            return true;
        }

        return false;
    }

    /**
     * 从DN中提取value值（第一个等号之后且第一个逗号之前的字符串）
     *
     * @param dn LDAP DN字符串
     * @return 提取的value值
     */
    private String extractValueFromDn(String dn) {
        if (dn == null || dn.isEmpty()) {
            return dn;
        }

        int equalsIndex = dn.indexOf("=");
        if (equalsIndex == -1) {
            return dn;
        }

        int commaIndex = dn.indexOf(",", equalsIndex + 1);
        if (commaIndex == -1) {
            // 如果没有逗号，返回等号之后的所有内容
            return dn.substring(equalsIndex + 1);
        }

        // 返回第一个等号之后且第一个逗号之前的字符串
        return dn.substring(equalsIndex + 1, commaIndex);
    }
}
