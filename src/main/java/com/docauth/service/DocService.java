package com.docauth.service;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.DocOwnerResponse;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.ConfigSecretKey;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocPasswordLog;

import com.docauth.entity.DocShareRel;
import com.docauth.repository.ConfigSecretKeyRepository;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocOperateLogRepository;

import com.docauth.repository.DocShareRelRepository;
import com.docauth.util.EccUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.ExampleMatcher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private ConfigSecretKeyRepository configSecretKeyRepository;

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
     * @return 解密后的密码响应对象
     * @throws RuntimeException 业务异常时抛出
     */
    public DocPasswordResponse getDocPassword(String docId, String encryPassword, String keyVersion) {
        log.info("[getDocPassword] 开始处理，docId: {}, keyVersion: {}", docId, keyVersion);

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
     * 保存操作日志（异步入口）
     *
     * @param docId                文档ID
     * @param path                 文件路径
     * @param keyVersion           密钥版本号
     * @param beforePassword       修改前密码（加密）
     * @param afterPassword        修改后密码（加密）
     * @param possiblePasswordList 可能的密码集合（加密）
     * @param platform             操作来源平台
     */
    public void saveLog(String docId, String path, String keyVersion, String beforePassword,
                        String afterPassword, List<String> possiblePasswordList, String platform) {
        log.info("[saveLog] 接收请求，docId: {}, path: {}, keyVersion: {}", docId, path, keyVersion);

        // 从Token中获取当前登录用户信息（在主线程中捕获）
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            currentAccount = "UNKNOW";
        }

        // 异步执行解密和保存操作
        asyncSaveLog(docId, path, keyVersion, beforePassword, afterPassword, 
                    possiblePasswordList, platform, currentAccount);
        
        log.info("[saveLog] 请求已提交到异步线程，docId: {}", docId);
    }

    /**
     * 异步保存操作日志
     *
     * @param docId                文档ID
     * @param path                 文件路径
     * @param keyVersion           密钥版本号
     * @param beforePassword       修改前密码（加密）
     * @param afterPassword        修改后密码（加密）
     * @param possiblePasswordList 可能的密码集合（加密）
     * @param platform             操作来源平台
     * @param currentAccount       当前用户账号（从主线程传递）
     */
    @org.springframework.scheduling.annotation.Async("logSaveExecutor")
    public void asyncSaveLog(String docId, String path, String keyVersion, String beforePassword,
                              String afterPassword, List<String> possiblePasswordList, 
                              String platform, String currentAccount) {
        log.info("[asyncSaveLog] 开始异步处理，docId: {}, path: {}, keyVersion: {}, user: {}", 
                docId, path, keyVersion, currentAccount);

        try {

            // 根据keyVersion从ConfigSecretKey中获取私钥
            String actualKeyVersion = keyVersion != null && !keyVersion.isEmpty() ? keyVersion : "default";
            ConfigSecretKey configSecretKey = configSecretKeyRepository.findByKeyVersion(actualKeyVersion)
                    .orElseThrow(() -> new RuntimeException("未找到对应的配置密钥，keyVersion: " + actualKeyVersion));

            // 使用ECC私钥解密beforePassword
            String decryptedBeforePassword = null;
            if (beforePassword != null && !beforePassword.isEmpty()) {
                try {
                    decryptedBeforePassword = EccUtil.decrypt(beforePassword, configSecretKey.getPrivateKey());
                    log.info("[asyncSaveLog] beforePassword解密成功");
                } catch (Exception e) {
                    log.error("[asyncSaveLog] beforePassword解密失败: {}", e.getMessage(), e);
                    throw new RuntimeException("beforePassword解密失败", e);
                }
            }

            // 使用ECC私钥解密afterPassword
            String decryptedAfterPassword = null;
            if (afterPassword != null && !afterPassword.isEmpty()) {
                try {
                    decryptedAfterPassword = EccUtil.decrypt(afterPassword, configSecretKey.getPrivateKey());
                    log.info("[asyncSaveLog] afterPassword解密成功");
                } catch (Exception e) {
                    log.error("[asyncSaveLog] afterPassword解密失败: {}", e.getMessage(), e);
                    throw new RuntimeException("afterPassword解密失败", e);
                }
            }

            // 使用ECC私钥解密possiblePasswordList中的每个密码
            List<String> decryptedPossiblePasswordList = null;
            if (possiblePasswordList != null && !possiblePasswordList.isEmpty()) {
                decryptedPossiblePasswordList = new ArrayList<>();
                for (int i = 0; i < possiblePasswordList.size(); i++) {
                    String encryptedPassword = possiblePasswordList.get(i);
                    if (encryptedPassword != null && !encryptedPassword.isEmpty()) {
                        try {
                            String decryptedPassword = EccUtil.decrypt(encryptedPassword, configSecretKey.getPrivateKey());
                            decryptedPossiblePasswordList.add(decryptedPassword);
                        } catch (Exception e) {
                            log.error("[asyncSaveLog] possiblePasswordList[{}]解密失败: {}", i, e.getMessage(), e);
                            throw new RuntimeException("possiblePasswordList第" + (i + 1) + "个密码解密失败", e);
                        }
                    }
                }
                log.info("[asyncSaveLog] possiblePasswordList解密成功，数量: {}", decryptedPossiblePasswordList.size());
            }

            // 对解密后的possiblePasswordList进行自然排序
            if (decryptedPossiblePasswordList != null && !decryptedPossiblePasswordList.isEmpty()) {
                decryptedPossiblePasswordList.sort(String.CASE_INSENSITIVE_ORDER);
                log.info("[asyncSaveLog] 密码列表已自然排序，数量: {}", decryptedPossiblePasswordList.size());
            }

            // 将解密后的可能的密码集合转换为JSON字符串存储
            String jsonPassword = null;
            if (decryptedPossiblePasswordList != null && !decryptedPossiblePasswordList.isEmpty()) {
                try {
                    jsonPassword = objectMapper.writeValueAsString(decryptedPossiblePasswordList);
                } catch (Exception e) {
                    log.error("[asyncSaveLog] 转换密码集合为JSON失败: {}", e.getMessage(), e);
                }
            }

            // 如果当前用户是UNKNOW，直接插入数据
            if ("UNKNOW".equals(currentAccount)) {
                saveNewLog(docId, currentAccount, path, decryptedBeforePassword, decryptedAfterPassword, jsonPassword, platform);
                log.info("[asyncSaveLog] UNKNOW用户，日志直接插入成功，docId: {}", docId);
                return;
            }

            // 普通用户：根据uid、path、beforePassword、afterPassword、possiblePassword、platform、createBy查询数据是否存在
            ExampleMatcher matcher = ExampleMatcher.matching()
                    .withMatcher("uid", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("path", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("beforePassword", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("afterPassword", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("possiblePassword", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("platform", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withMatcher("createBy", ExampleMatcher.GenericPropertyMatchers.exact())
                    .withIgnorePaths("id", "createTime", "updateTime", "updateBy");

            DocPasswordLog queryLog = new DocPasswordLog();
            queryLog.setUid(docId);
            queryLog.setPath(path);
            queryLog.setBeforePassword(decryptedBeforePassword);
            queryLog.setAfterPassword(decryptedAfterPassword);
            queryLog.setPossiblePassword(jsonPassword);
            queryLog.setPlatform(platform);
            queryLog.setCreateBy(currentAccount);

            Example<DocPasswordLog> example = Example.of(queryLog, matcher);
            Optional<DocPasswordLog> existingLogOpt = docOperateLogRepository.findOne(example);

            if (existingLogOpt.isPresent()) {
                // 如果存在，设置updateBy并触发save以让数据库的ON UPDATE CURRENT_TIMESTAMP生效
                DocPasswordLog existingLog = existingLogOpt.get();
                existingLog.setUpdateBy(currentAccount);
                docOperateLogRepository.save(existingLog);
                log.info("[asyncSaveLog] 日志已存在，updateTime和updateBy由数据库自动更新，docId: {}", docId);
            } else {
                // 如果不存在，创建新的日志记录
                saveNewLog(docId, currentAccount, path, decryptedBeforePassword, decryptedAfterPassword, jsonPassword, platform);
                log.info("[asyncSaveLog] 日志保存成功，docId: {}", docId);
            }
        } catch (Exception e) {
            log.error("[asyncSaveLog] 异步保存日志失败，docId: {}, error: {}", docId, e.getMessage(), e);
            // 异步方法中的异常不会影响主流程，只记录日志
        }
    }

    /**
     * 创建并保存新的日志记录
     */
    private void saveNewLog(String docId, String createBy, String path,
                            String beforePassword, String afterPassword,
                            String possiblePassword, String platform) {
        DocPasswordLog passwordLog = new DocPasswordLog();
        passwordLog.setUid(docId);
        passwordLog.setCreateBy(createBy);
        passwordLog.setPath(path);
        
        // 从path中提取文件名
        if (path != null && !path.isEmpty()) {
            int lastSlashIndex = path.lastIndexOf('/');
            if (lastSlashIndex == -1) {
                lastSlashIndex = path.lastIndexOf('\\');
            }
            String fileName = (lastSlashIndex != -1) ? path.substring(lastSlashIndex + 1) : path;
            passwordLog.setFileName(fileName);
        }
        
        passwordLog.setBeforePassword(beforePassword);
        passwordLog.setAfterPassword(afterPassword);
        passwordLog.setPossiblePassword(possiblePassword);
        passwordLog.setPlatform(platform);
        docOperateLogRepository.save(passwordLog);
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
