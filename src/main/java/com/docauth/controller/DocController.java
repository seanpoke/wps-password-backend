package com.docauth.controller;

import com.docauth.dto.ApiResponse;
import com.docauth.dto.DocInfoResponse;
import com.docauth.dto.DocPasswordRequest;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.DocUpdateRelRequest;
import com.docauth.dto.LdapItem;
import com.docauth.entity.DocInfo;
import com.docauth.entity.DocOperateLog;
import com.docauth.entity.DocShareRel;
import com.docauth.repository.DocInfoRepository;
import com.docauth.repository.DocOperateLogRepository;
import com.docauth.repository.DocShareRelRepository;
import com.docauth.service.LdapService;
import com.docauth.util.RedisUtil;
import com.docauth.util.RsaUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/doc")
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
    private RedisUtil redisUtil;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @GetMapping("/info")
    public ApiResponse<?> getDocInfo(@RequestParam String docId, HttpServletRequest request) {
        // 从请求中获取当前用户账号
        String account = (String) request.getAttribute("account");
        
        // 校验docId非空
        if (docId == null || docId.isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId不能为空");
        }
        
        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(docId);
        
        if (docInfo == null) {
            // 生成公私钥对
            try {
                String[] keyPair = RsaUtil.generateKeyPair();
                String publicKey = keyPair[0];
                String privateKey = keyPair[1];
                
                // 创建新的文件信息记录
                docInfo = new DocInfo();
                docInfo.setUid(docId);
                docInfo.setPublicKey(publicKey);
                docInfo.setPrivateKey(privateKey);
                docInfo.setOwner(account);
                docInfo.setCreateBy(account);
                
                // 保存到数据库
                docInfoRepository.save(docInfo);
            } catch (Exception e) {
                e.printStackTrace();
                return ApiResponse.error(500, "生成密钥失败");
            }
        }
        
        // 构建响应
        DocInfoResponse response = new DocInfoResponse();
        response.setPublicKey(docInfo.getPublicKey());
        response.setOwner(docInfo.getOwner());
        response.setUid(docInfo.getUid());
        
        return ApiResponse.success(response);
    }
    
    @PostMapping("/password")
    public ApiResponse<?> getDocPassword(@RequestBody DocPasswordRequest request, HttpServletRequest httpRequest) {
        // 从请求中获取当前用户账号
        String currentAccount = (String) httpRequest.getAttribute("account");
        
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
            request.getAccount() == null || request.getAccount().isEmpty() ||
            request.getEncryPassword() == null || request.getEncryPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId、account和encryPassword不能为空");
        }
        
        // 从数据库查询文件信息
        DocInfo docInfo = docInfoRepository.findByUid(request.getDocId());
        if (docInfo == null) {
            return ApiResponse.error(409, "文件不存在");
        }
        
        // 校验account是否有该文档的访问权限
        DocShareRel shareRel = docShareRelRepository.findByUidAndRelTypeAndValue(request.getDocId(), 0, request.getAccount());
        if (shareRel == null) {
            return ApiResponse.error(401, "无访问权限");
        }
        
        // 使用私钥解密
        try {
            String password = RsaUtil.decrypt(request.getEncryPassword(), docInfo.getPrivateKey());
            
            // 插入操作日志
            DocOperateLog log = new DocOperateLog();
            log.setUid(request.getDocId());
            log.setType("extrac_password");
            log.setCreateBy(currentAccount);
            docOperateLogRepository.save(log);
            
            // 构建响应
            DocPasswordResponse response = new DocPasswordResponse();
            response.setPassword(password);
            return ApiResponse.success(response);
        } catch (Exception e) {
            e.printStackTrace();
            return ApiResponse.error(500, "解密失败");
        }
    }
    
    @GetMapping("/ldap/info")
    public ApiResponse<?> getLdapInfo() {
        // 从Redis查询LDAP结构缓存
        String cacheKey = "LDAP_STRUCTURE";
        String cachedData = redisUtil.get(cacheKey);
        
        if (cachedData != null) {
            try {
                // 缓存存在，直接返回
                List<LdapItem> ldapItems = objectMapper.readValue(cachedData, objectMapper.getTypeFactory().constructCollectionType(List.class, LdapItem.class));
                return ApiResponse.success(ldapItems);
            } catch (Exception e) {
                e.printStackTrace();
                // 缓存解析失败，继续查询
            }
        }
        
        // 缓存不存在或解析失败，调用LDAP接口查询
        List<LdapItem> ldapItems = new ArrayList<>();
        

        
        // 对查询结果进行内存排序（按name升序）
        ldapItems.sort((a, b) -> a.getName().compareTo(b.getName()));
        
        // 将排序后的数据存入Redis，设置24小时过期
        try {
            String jsonData = objectMapper.writeValueAsString(ldapItems);
            redisUtil.set(cacheKey, jsonData, 24, TimeUnit.HOURS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return ApiResponse.success(ldapItems);
    }
    
    @PostMapping("/updateRel")
    @Transactional
    public ApiResponse<?> updateDocRel(@RequestBody DocUpdateRelRequest request, HttpServletRequest httpRequest) {
        // 从请求中获取当前用户账号
        String currentAccount = (String) httpRequest.getAttribute("account");
        
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
            request.getOperate() == null || request.getOperate().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId和operate不能为空");
        }
        
        // 从数据库查询文件信息，获取owner
        DocInfo docInfo = docInfoRepository.findByUid(request.getDocId());
        if (docInfo == null) {
            return ApiResponse.error(409, "文件不存在");
        }
        
        // 校验token解析的用户账号与owner一致（仅所有者可操作）
        if (!docInfo.getOwner().equals(currentAccount)) {
            return ApiResponse.error(401, "无操作权限，仅文档所有者可更新授权");
        }
        
        // 删除doc_share_rel表中该docId的所有旧授权记录
        List<DocShareRel> oldRelations = docShareRelRepository.findByUid(request.getDocId());
        if (!oldRelations.isEmpty()) {
            docShareRelRepository.deleteAll(oldRelations);
        }
        
        // 遍历accountFullList，插入用户授权记录
        if (request.getAccountFullList() != null && !request.getAccountFullList().isEmpty()) {
            for (String account : request.getAccountFullList()) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(request.getDocId());
                shareRel.setRelType(0); // 0表示用户
                shareRel.setValue(account);
                shareRel.setFullPath(ldapService.getUserPath(account));
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
        }
        
        // 遍历deptFullList，插入部门授权记录
        if (request.getDeptFullList() != null && !request.getDeptFullList().isEmpty()) {
            for (String dept : request.getDeptFullList()) {
                DocShareRel shareRel = new DocShareRel();
                shareRel.setUid(request.getDocId());
                shareRel.setRelType(1); // 1表示部门
                shareRel.setValue(dept);
                shareRel.setFullPath(ldapService.getDeptPath(dept));
                shareRel.setCreateBy(currentAccount);
                docShareRelRepository.save(shareRel);
            }
        }
        
        return ApiResponse.success("操作成功");
    }
}