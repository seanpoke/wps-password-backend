package com.docauth.controller;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.DocOwnerResponse;
import com.docauth.dto.DocPasswordRequest;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.DocUpdateRelRequest;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.dto.SaveLogRequest;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocPasswordLog;
import com.docauth.entity.DocShareRel;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocOperateLogRepository;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.service.ConfigService;
import com.docauth.service.LdapService;
import com.docauth.util.RsaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/doc")
@Tag(name = "文档管理", description = "文档密码获取、授权管理、操作日志相关接口")
public class DocController {

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


    @GetMapping("/owner")
    @Operation(summary = "获取文档所有者信息", description = "根据文档ID查询文档的所有者信息，如果文档不存在则自动创建")
    public ApiResponse<?> getDocOwner(@Parameter(description = "文档ID", required = true) @RequestParam String docId) {
        log.info("[getDocOwner] 请求参数: docId={}", docId);
        // 校验docId非空
        if (docId == null || docId.isEmpty()) {
            return ApiResponse.error(400, "参数错误：文档id不能为空");
        }

        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(docId);

        if (docInfo == null) {
            // 从ThreadLocal获取当前登录用户信息
            String account = UserContextHolder.getCurrentAccount();
            String name = UserContextHolder.getCurrentName();
            if (account == null || account.isEmpty()) {
                return ApiResponse.error(401, "未授权：无法获取当前用户");
            }

            // 从数据库读取RSA密钥对
            try {
                String publicKey = configService.getPublicKey();
                String privateKey = configService.getPrivateKey();
                
                if (publicKey == null || privateKey == null) {
                    return ApiResponse.error(500, "系统配置错误：未找到RSA密钥配置");
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
            } catch (Exception e) {
                e.printStackTrace();
                return ApiResponse.error(500, "创建文档信息失败");
            }
        }

        // 构建响应
        DocOwnerResponse response = new DocOwnerResponse();
        response.setAccount(docInfo.getAccount());
        response.setName(docInfo.getName());

        return ApiResponse.success(response);
    }

    @PostMapping("/password")
    @Operation(summary = "获取文档密码", description = "通过RSA加密的密码获取文档解密后的密码，需要验证用户权限")
    public ApiResponse<?> getDocPassword(@RequestBody DocPasswordRequest request) {
        log.info("[getDocPassword] 请求参数: docId={}, encryPassword={}",
                request.getDocId(), request.getEncryPassword());
        
        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            return ApiResponse.error(401, "未授权：无法获取当前用户");
        }
        
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
                request.getEncryPassword() == null || request.getEncryPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId和encryPassword不能为空");
        }

        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(request.getDocId());
        if (docInfo == null) {
            return ApiResponse.error(409, "文件不存在");
        }

        // 第一步：校验当前用户是否为文档所属人
        if (!docInfo.getAccount().equals(currentAccount)) {
            // 如果不是文档所有人，继续执行后续权限判断

            // 第二步：根据docId查询账号权限的dnList集合
            List<DocShareRel> shareRels = docShareRelRepository.findByUid(request.getDocId());
            if (shareRels == null || shareRels.isEmpty()) {
                return ApiResponse.error(401, "无访问权限");
            }

            // 提取所有授权的DN路径
            List<String> authorizedDnList = new ArrayList<>();
            for (DocShareRel rel : shareRels) {
                if (rel.getDn() != null && !rel.getDn().isEmpty()) {
                    authorizedDnList.add(rel.getDn());
                }
            }

            if (authorizedDnList.isEmpty()) {
                return ApiResponse.error(401, "无访问权限");
            }

            // 第三步：通过ldap查询当前用户的dn
            String accountDn = ldapService.getUserDn(currentAccount);
            if (accountDn == null || accountDn.isEmpty()) {
                return ApiResponse.error(401, "无法获取用户LDAP信息");
            }

            // 第四步：判断账号的dn是否属于"集合中某条路径"的子集（或就是该路径本身）
            boolean hasPermission = false;
            for (String authorizedDn : authorizedDnList) {
                // 判断accountDn是否是authorizedDn的子路径或相同路径
                if (isDnSubPath(accountDn, authorizedDn)) {
                    hasPermission = true;
                    break;
                }
            }

            if (!hasPermission) {
                return ApiResponse.error(401, "无访问权限");
            }
        }

        // 使用私钥解密
        try {
            String password = RsaUtil.decrypt(request.getEncryPassword(), docInfo.getPrivateKey());
            // 构建响应
            DocPasswordResponse response = new DocPasswordResponse();
            response.setPassword(password);
            return ApiResponse.success(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(500, "解密失败");
        }
    }

    @GetMapping("/auth/tree")
    @Operation(summary = "获取授权树", description = "获取LDAP组织结构的授权树，支持按文档ID过滤已授权节点")
    public ApiResponse<?> getAuthTree(@Parameter(description = "文档ID，可选参数") @RequestParam(required = false) String docId) {
        log.info("[getAuthTree] 请求参数: docId={}", docId);
        List<LdapNodeDTO> ldapTree = ldapService.getLdapTreeWithAuth(docId);
        return ApiResponse.success(ldapTree);
    }

    @PostMapping("/auth/update")
    @Transactional
    @Operation(summary = "更新文档授权", description = "更新文档的访问授权，仅文档所有者可操作")
    public ApiResponse<?> updateDocAuth(@RequestBody DocUpdateRelRequest request) {
        log.info("[updateDocAuth] 请求参数: docId={}, account={}, accountDnList size={}, deptDnList size={}",
                request.getDocId(),
                request.getAccountDnList() != null ? request.getAccountDnList().size() : 0,
                request.getDeptDnList() != null ? request.getDeptDnList().size() : 0);
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId不能为空");
        }

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            return ApiResponse.error(401, "未授权：无法获取当前用户");
        }

        // 从数据库查询文件信息，获取owner
        DocInfo docInfo = docInfoRepository.findByUid(request.getDocId());
        if (docInfo == null) {
            return ApiResponse.error(409, "文件不存在");
        }

        // 校验当前用户与owner一致（仅所有者可操作）
        if (!docInfo.getAccount().equals(currentAccount)) {
            return ApiResponse.error(401, "无操作权限，仅文档所有者可更新授权");
        }

        // 删除doc_share_rel表中该docId的所有旧授权记录
        List<DocShareRel> oldRelations = docShareRelRepository.findByUid(request.getDocId());
        if (!oldRelations.isEmpty()) {
            docShareRelRepository.deleteAll(oldRelations);
        }

        // 遍历accountDnList，插入用户授权记录
        if (request.getAccountDnList() != null && !request.getAccountDnList().isEmpty()) {
            for (String dn : request.getAccountDnList()) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(request.getDocId());
                shareRel.setType(0); // 0表示用户
                String value = extractValueFromDn(dn);
                shareRel.setName(value);
                shareRel.setDn(dn);
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
        }

        // 遍历deptDnList，插入部门授权记录
        if (request.getDeptDnList() != null && !request.getDeptDnList().isEmpty()) {
            for (String dn : request.getDeptDnList()) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(request.getDocId());
                shareRel.setType(1); // 1表示部门
                String value = extractValueFromDn(dn);
                shareRel.setName(value);
                shareRel.setDn(dn);
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
        }

        return ApiResponse.success("操作成功");
    }

    @PostMapping("/save/log")
    @Operation(summary = "保存操作日志", description = "记录文档密码修改等操作日志")
    public ApiResponse<?> saveLog(@RequestBody SaveLogRequest request) {
        log.info("[saveLog] 请求参数: docId={}, path={}, beforePassword={}, afterPassword={}",
                request.getDocId(), request.getPath(),
                request.getBeforePassword(), request.getAfterPassword());

        // 从Token中获取当前登录用户信息
        String currentAccount = UserContextHolder.getCurrentAccount();
        if (currentAccount == null || currentAccount.isEmpty()) {
            currentAccount = "UNKNOW";
        }

        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
                request.getPath() == null || request.getPath().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId和path不能为空");
        }

        // 创建操作日志
        DocPasswordLog log = new DocPasswordLog();
        log.setUid(request.getDocId());
        log.setType("save");
        log.setCreateBy(currentAccount);
        log.setPath(request.getPath());
        log.setBeforePassword(request.getBeforePassword());
        log.setAfterPassword(request.getAfterPassword());

        // 将可能的密码集合转换为JSON字符串存储
        if (request.getPossiblePasword() != null && !request.getPossiblePasword().isEmpty()) {
            try {
                String jsonPassword = objectMapper.writeValueAsString(request.getPossiblePasword());
                log.setPossiblePassword(jsonPassword);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        docOperateLogRepository.save(log);

        return ApiResponse.success("操作成功");
    }

    /**
     * 判断accountDn是否是authorizedDn的子路径或相同路径
     * 例如：
     * - accountDn: "cn=张三,ou=技术部,dc=example,dc=com"
     * - authorizedDn: "ou=技术部,dc=example,dc=com"
     * - 结果：true (accountDn是authorizedDn的子路径)
     * <p>
     * - accountDn: "ou=技术部,dc=example,dc=com"
     * - authorizedDn: "ou=技术部,dc=example,dc=com"
     * - 结果：true (相同路径)
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
     * 例如：cn=张三,ou=部门,dc=example,dc=com -> 张三
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