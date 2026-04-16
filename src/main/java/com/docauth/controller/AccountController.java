package com.docauth.controller;

import com.docauth.context.UserContext;
import com.docauth.dto.ApiResponse;
import com.docauth.dto.LoginRequest;
import com.docauth.dto.LoginResponse;
import com.docauth.service.LdapService;
import com.docauth.util.RedisUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@Tag(name = "账户管理", description = "用户登录认证相关接口")
public class AccountController {

    @Autowired
    private LdapService ldapService;

    @Autowired
    private RedisUtil redisUtil;

    @PostMapping("/account/login")
    @Operation(summary = "用户登录", description = "通过账号密码进行登录，返回token用于后续请求鉴权")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        log.info("[login] 请求参数: account={}", request.getAccount());
        // 校验参数非空
        if (request.getAccount() == null || request.getAccount().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：账号和密码不能为空");
        }
        if ((request.getAccount().equals("admin"))) {
            return ApiResponse.error(400, "参数错误：不能使用管理员账号登录");
        }

        // 调用LDAP服务校验用户身份并获取用户信息
        UserContext userContext = ldapService.authenticate(request.getAccount(), request.getPassword());
        if (userContext == null) {
            return ApiResponse.error(401, "认证失败：账号或密码错误");
        }

        // 生成token
        String token = UUID.randomUUID().toString();

        // 将用户对象存储到Redis，设置72小时过期
        redisUtil.setObject(token, userContext, 72, TimeUnit.HOURS);

        log.info("[login] 登录成功，账号: {}, 姓名: {}", userContext.getAccount(), userContext.getName());

        // 返回token、account和name
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setAccount(userContext.getAccount());
        response.setName(userContext.getName());
        return ApiResponse.success(response);
    }
}