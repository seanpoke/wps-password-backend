package com.docauth.controller;

import com.docauth.context.UserContextHolder;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.LoginRequest;
import com.docauth.dto.LoginResponse;
import com.docauth.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@Tag(name = "账户管理", description = "用户登录认证相关接口")
public class AccountController {

    @Autowired
    private AccountService accountService;

    @PostMapping("/account/login")
    @Operation(summary = "用户登录", description = "通过账号密码进行登录，返回token用于后续请求鉴权")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        log.info("[login] 请求参数: account={}", request.getAccount());

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
            // 调用Service处理登录业务逻辑
            LoginResponse response = accountService.login(request.getAccount(), request.getPassword());
            return ApiResponse.success(response);
        } catch (RuntimeException e) {
            log.warn("[login] 登录失败: {}", e.getMessage());
            return ApiResponse.error(401, e.getMessage());
        } catch (Exception e) {
            log.error("[login] 登录异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "登录失败：系统异常");
        }
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

            return ApiResponse.success(response);
        } catch (Exception e) {
            log.error("[refreshToken] token刷新异常: {}", e.getMessage(), e);
            return ApiResponse.error(500, "token刷新失败：系统异常");
        }
    }
}