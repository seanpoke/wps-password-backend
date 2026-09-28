package com.docauth.controller;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.LoginRequest;
import com.docauth.dto.LoginResponse;
import com.docauth.dto.ChangePasswordRequest;
import com.docauth.entity.SysRole;
import com.docauth.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Tag(name = "账户管理", description = "用户登录认证相关接口")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @PostMapping("/account/login")
    @Operation(summary = "用户登录", description = "通过账号密码进行登录，返回token用于后续请求鉴权")
    public ApiResponse<?> login(@RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("[login] 请求参数: account={}, ip={}", request.getAccount(), clientIp);

        // 校验参数非空
        if (request.getAccount() == null || request.getAccount().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：账号和密码不能为空");
        }

        // 校验不能使用管理员账号登录
        if ("admin".equals(request.getAccount())) {
            return ApiResponse.error(400, "参数错误：不能使用管理员账号登录");
        }

        try {
            // 调用Service处理登录业务逻辑（传入客户端 IP 用于失败次数限流）
            LoginResponse response = accountService.login(request.getAccount(), request.getPassword(), clientIp);
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            log.warn("[login] 登录失败: {}", e.getMessage());
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("[login] 登录异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "登录失败：系统异常");
        }
    }

    /**
     * 获取客户端真实 IP：优先 X-Forwarded-For（取第一个），其次 X-Real-IP，最后远端地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            if (idx > 0) {
                ip = ip.substring(0, idx).trim();
            }
            return ip;
        }
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return request.getRemoteAddr();
    }

    @PostMapping("/account/refresh-token")
    @Operation(summary = "刷新token", description = "用于心跳保活，延长token过期时间，保持用户登录状态。TokenInterceptor会自动刷新token过期时间。")
    public ApiResponse<?> refreshToken(@RequestHeader("token") String token) {
        log.info("[refreshToken] 接收到token刷新请求{}", token);

        try {
            // 从Token拦截器设置的UserContextHolder中获取当前用户信息
            String currentAccount = UserContextHolder.getCurrentAccount();
            String currentName = UserContextHolder.getCurrentName();

            if (currentAccount == null || currentAccount.isEmpty()) {
                return ApiResponse.error(401, "未授权：用户未登录");
            }

            // 构建响应（TokenInterceptor已经自动刷新了token过期时间）
            LoginResponse response = new LoginResponse();
            response.setToken(token);  // 返回当前token
            response.setAccount(currentAccount);
            response.setName(currentName);

            // 查询用户角色（多角色取并集，role 为优先级最高的角色 code）
            List<SysRole> roles = accountService.getUserRoles(currentAccount);
            String role = accountService.getPrimaryRole(roles);
            response.setRole(role);

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("[refreshToken] token刷新异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "token刷新失败：系统异常");
        }
    }

    @PostMapping("/account/logout")
    @Operation(summary = "用户登出", description = "用户登出")
    public ApiResponse<?> logout(@RequestHeader("token") String token) {
        log.info("[logout] 接收到登出请求");

        try {
            // 调用Service处理登出业务逻辑（删除Redis中的token）
            accountService.logout(token);
            return ApiResponse.success("登出成功");
        } catch (Exception e) {
            log.error("[logout] 登出异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "登出失败：系统异常");
        }
    }

    @PostMapping("/account/change-password")
    @Operation(summary = "修改密码", description = "本地用户修改密码（首登改密或主动修改），LDAP 用户不支持。无需 token，按请求参数中的账号校验。")
    public ApiResponse<?> changePassword(@RequestBody ChangePasswordRequest request, HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        log.info("[changePassword] 接收到密码修改请求，账号: {}, ip: {}", request.getAccount(), clientIp);

        if (request.getAccount() == null || request.getAccount().isEmpty()
                || request.getOldPassword() == null || request.getOldPassword().isEmpty()
                || request.getNewPassword() == null || request.getNewPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：账号、原密码和新密码不能为空");
        }

        try {
            // 传入客户端 IP，与登录共用 ip_账号 失败次数限流
            accountService.changePassword(request.getAccount(), request.getOldPassword(), request.getNewPassword(), clientIp);
            return ApiResponse.success("密码修改成功");
        } catch (RuntimeException e) {
            log.warn("[changePassword] 失败: {}", e.getMessage());
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("[changePassword] 异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "密码修改失败：系统异常");
        }
    }
}
