package com.docauth.controller;

import com.docauth.dto.ApiResponse;
import com.docauth.dto.DocOwnerRequest;
import com.docauth.dto.DocOwnerResponse;
import com.docauth.dto.DocPasswordRequest;
import com.docauth.dto.DocPasswordResponse;
import com.docauth.dto.DocUpdateRelRequest;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.dto.SaveLogRequest;
import com.docauth.service.DocService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/doc")
@Tag(name = "文档管理", description = "文档密码获取、授权管理、操作日志相关接口")
public class DocController {

    @Autowired
    private DocService docService;


    @PostMapping("/owner")
    @Operation(summary = "获取文档所有者信息", description = "根据文档ID查询文档的所有者信息，如果文档不存在则自动创建")
    public ApiResponse<?> getDocOwner(@RequestBody DocOwnerRequest request) {
        log.info("[getDocOwner] 请求参数: docId={}, fileName={}", request.getDocId(), request.getFileName());
        
        // 校验docId非空
        if (request.getDocId() == null || request.getDocId().isEmpty()) {
            return ApiResponse.error(400, "参数错误：文档id不能为空");
        }

        try {
            // 调用Service处理业务逻辑
            DocOwnerResponse response = docService.getDocOwner(request.getDocId(), request.getFileName());
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            log.warn("[getDocOwner] 业务异常: {}", e.getMessage());
            if (e.getMessage().contains("未授权")) {
                return ApiResponse.error(401, e.getMessage());
            }
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("[getDocOwner] 系统异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取文档所有者失败：系统异常");
        }
    }

    @PostMapping("/password")
    @Operation(summary = "获取文档密码", description = "通过RSA加密的密码获取文档解密后的密码，需要验证用户权限")
    public ApiResponse<?> getDocPassword(@RequestBody DocPasswordRequest request) {
        log.info("[getDocPassword] 请求参数: docId={}, keyVersion={}", request.getDocId(), request.getKeyVersion());
            
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
                request.getEncryPassword() == null || request.getEncryPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId和encryPassword不能为空");
        }
    
        try {
            // 调用Service处理业务逻辑
            DocPasswordResponse response = docService.getDocPassword(
                request.getDocId(), 
                request.getEncryPassword(), 
                request.getKeyVersion(),
                request.getIsTemp()
            );
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            log.warn("[getDocPassword] 业务异常: {}", e.getMessage());
            if (e.getMessage().contains("未授权")) {
                return ApiResponse.error(401, e.getMessage());
            } else if (e.getMessage().contains("无访问权限") || e.getMessage().contains("文件不存在")) {
                return ApiResponse.error(409, e.getMessage());
            }
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("[getDocPassword] 系统异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取文档密码失败：系统异常");
        }
    }

    @GetMapping("/auth/tree")
    @Operation(summary = "获取授权树", description = "获取LDAP组织结构的授权树，支持按文档ID过滤已授权节点")
    public ApiResponse<?> getAuthTree(@Parameter(description = "文档ID，可选参数") @RequestParam(required = false) String docId) {
        log.info("[getAuthTree] 请求参数: docId={}", docId);
        
        try {
            // 调用Service处理业务逻辑
            List<LdapNodeDTO> ldapTree = docService.getAuthTree(docId);
            return ApiResponse.success(ldapTree);
        } catch (Exception e) {
            log.error("[getAuthTree] 系统异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "获取授权树失败：系统异常");
        }
    }

    @PostMapping("/auth/update")
    @Operation(summary = "更新文档授权", description = "更新文档的访问授权，仅文档所有者可操作")
    public ApiResponse<?> updateDocAuth(@RequestBody DocUpdateRelRequest request) {
        log.info("[updateDocAuth] 请求参数: docId={}", request.getDocId());
        
        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId不能为空");
        }

        try {
            // 调用Service处理业务逻辑
            docService.updateDocAuth(request.getDocId(), request.getAccountDnList(), request.getDeptDnList(), request.getIsTemp());
            return ApiResponse.success("操作成功");
        } catch (RuntimeException e) {
            log.warn("[updateDocAuth] 业务异常: {}", e.getMessage());
            if (e.getMessage().contains("未授权")) {
                return ApiResponse.error(401, e.getMessage());
            } else if (e.getMessage().contains("文件不存在") || e.getMessage().contains("无操作权限")) {
                return ApiResponse.error(409, e.getMessage());
            }
            return ApiResponse.error(500, e.getMessage());
        } catch (Exception e) {
            log.error("[updateDocAuth] 系统异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "更新文档授权失败：系统异常");
        }
    }

    @PostMapping("/save/log")
    @Operation(summary = "保存操作日志", description = "记录文档密码修改等操作日志")
    public ApiResponse<?> saveLog(@RequestBody SaveLogRequest request) {
        log.info("[saveLog] 请求参数: docId={}, path={}, keyVersion={}", request.getDocId(), request.getPath(), request.getKeyVersion());

        // 校验参数非空
        if (request.getDocId() == null || request.getDocId().isEmpty() ||
                request.getPath() == null || request.getPath().isEmpty() ||
                request.getKeyVersion() == null || request.getKeyVersion().isEmpty()) {
            return ApiResponse.error(400, "参数错误：docId、path和keyVersion不能为空");
        }

        try {
            // 调用Service处理业务逻辑，传入加密的密码和keyVersion
            docService.saveLog(
                request.getDocId(),
                request.getPath(),
                request.getKeyVersion(),
                request.getBeforePassword(),
                request.getAfterPassword(),
                request.getPossiblePassword(),
                request.getPlatform()
            );
            return ApiResponse.success("操作成功");
        } catch (Exception e) {
            log.error("[saveLog] 系统异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "保存日志失败：系统异常");
        }
    }
}
