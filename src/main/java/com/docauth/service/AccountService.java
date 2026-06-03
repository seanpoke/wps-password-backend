package com.docauth.service;

import com.docauth.context.UserContext;
import com.docauth.dto.LoginResponse;
import com.docauth.entity.SysRole;
import com.docauth.repository.SysRoleRepository;
import com.docauth.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 账户服务 - 处理用户登录认证相关业务逻辑
 */
@Slf4j
@Service
public class AccountService {

    @Autowired
    private LdapService ldapService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private com.docauth.service.ConfigService configService;

    @Autowired
    private SysRoleRepository sysRoleRepository;

    /**
     * 用户登录业务逻辑
     *
     * @param account  账号
     * @param password 密码
     * @return 登录响应对象，包含token、account和name
     * @throws IllegalArgumentException 参数错误时抛出
     * @throws RuntimeException         认证失败时抛出
     */
    public LoginResponse login(String account, String password) {
        log.info("[login] 开始处理登录请求，账号: {}", account);

        // 调用LDAP服务校验用户身份并获取用户信息
        UserContext userContext = ldapService.authenticate(account, password);
        if (userContext == null) {
            log.warn("[login] 认证失败，账号: {}", account);
            throw new RuntimeException("认证失败：账号或密码错误");
        }

        // 生成token
        String token = UUID.randomUUID().toString();

        // 从配置中获取Redis Token过期时间（单位：分钟）
        Long expireMinutes = configService.getRedisTokenExpireMinutes();
        // 将用户对象存储到Redis
        redisUtil.setObject(token, userContext, expireMinutes, TimeUnit.MINUTES);

        log.info("[login] 登录成功，账号: {}, 姓名: {}, Token过期时间: {} 分钟", userContext.getAccount(), userContext.getName(), expireMinutes);

        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setAccount(userContext.getAccount());
        response.setName(userContext.getName());

        // 查询用户角色
        String role = getUserRole(userContext.getAccount());
        response.setRole(role);

        return response;
    }

    /**
     * 获取用户角色
     *
     * @param account 用户账号
     * @return 角色类型 ("admin" 超级管理员 / "user" 普通用户)，未找到时默认返回 "user"（普通用户）
     */
    private String getUserRole(String account) {
        if (account == null || account.isEmpty()) {
            return "user"; // 默认普通用户
        }

        try {
            java.util.Optional<SysRole> roleOpt = sysRoleRepository.findByAccount(account);
            if (roleOpt.isPresent()) {
                SysRole sysRole = roleOpt.get();
                String type = sysRole.getType();
                // 直接返回数据库中存储的角色值（admin 或 user）
                if ("admin".equals(type) || "user".equals(type)) {
                    return type;
                }
            }
        } catch (Exception e) {
            log.error("[getUserRole] 查询用户角色失败，account: {}, error: {}", account, e.getMessage(), e);
        }

        // 未查询到角色记录，默认返回普通用户
        return "user";
    }

    /**
     * 用户登出业务逻辑
     *
     * @param token 用户的访问令牌
     */
    public void logout(String token) {
        // 从Redis中获取用户对象
        UserContext userContext = redisUtil.getObject(token, UserContext.class);
        if (userContext == null) {
            log.info("[logout] token无效 {}", token);
        } else {
            log.info("[logout] 登出请求，token: {}, 账号: {}, 姓名: {}", token, userContext.getAccount(), userContext.getName());
            // 从Redis中删除token，使token立即失效
            redisUtil.delete(token);
        }
        log.info("[logout] 登出成功，token已失效");

    }
}
