package com.docauth.controller;

import com.docauth.dto.ApiResponse;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.dto.LoginRequest;
import com.docauth.dto.LoginResponse;
import com.docauth.service.LdapService;
import com.docauth.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class AccountController {

    @Autowired
    private LdapService ldapService;

    @Autowired
    private RedisUtil redisUtil;

    @PostMapping("/account/login")
    public ApiResponse<?> login(@RequestBody LoginRequest request) {
        log.info("接受到login请求,request={}", request);
        // 校验参数非空
        if (request.getAccount() == null || request.getAccount().isEmpty() ||
                request.getPassword() == null || request.getPassword().isEmpty()) {
            return ApiResponse.error(400, "参数错误：账号和密码不能为空");
        }
        if ((request.getAccount().equals("admin"))) {
            return ApiResponse.error(400, "参数错误：不能使用管理员账号登录");
        }

        // 调用LDAP服务校验用户身份
        boolean authenticated = ldapService.authenticate(request.getAccount(), request.getPassword());
        if (!authenticated) {
            return ApiResponse.error(401, "认证失败：账号或密码错误");
        }

        // 生成token
        String token = UUID.randomUUID().toString();

        // 存储token到Redis，设置72小时过期
        redisUtil.set(token, request.getAccount(), 72, TimeUnit.HOURS);

        // 返回token
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        return ApiResponse.success(response);
    }


    @GetMapping("/ldap/tree")
    public ApiResponse<?> getLdapTree() {
        List<LdapNodeDTO> allLdapTree = ldapService.getAllLdapTree();
        return ApiResponse.success(allLdapTree);
    }
}