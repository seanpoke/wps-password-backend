package com.docauth.service;

import com.docauth.context.UserContext;
import com.docauth.dto.LoginResponse;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysUserRole;
import com.docauth.repository.SysRoleRepository;
import com.docauth.repository.SysUserRepository;
import com.docauth.repository.SysUserRoleRepository;
import com.docauth.util.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 账户服务 - 处理用户登录认证相关业务逻辑
 * 支持 LDAP 内部用户与本地外部用户(BCrypt)双身份源
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

    @Autowired
    private SysUserRepository sysUserRepository;

    @Autowired
    private SysUserRoleRepository sysUserRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * 用户登录业务逻辑
     * 先尝试 LDAP 认证（内部用户），失败则回退本地用户(BCrypt)认证
     *
     * @param account  账号
     * @param password 密码（明文，传输层依赖 HTTPS）
     * @return 登录响应对象，包含 token、account、name、role、source
     */
    public LoginResponse login(String account, String password) {
        log.info("[login] 开始处理登录请求，账号: {}", account);

        // 1. 尝试 LDAP 认证（内部用户）
        UserContext userContext = ldapService.authenticate(account, password);
        String source = "LDAP";
        boolean needChangePwd = false;

        // 2. LDAP 未命中，回退本地外部用户认证（落库即为有效账号）
        if (userContext == null) {
            SysUser local = sysUserRepository.findByAccount(account).orElse(null);
            if (local == null) {
                throw new RuntimeException("认证失败：账号或密码错误");
            }
            // 仅本地账号走 BCrypt 校验；LDAP 账号即使落到本地表也不允许本地密码
            if (!"LOCAL".equals(local.getSource())) {
                throw new RuntimeException("认证失败：该账号非本地账号");
            }
            if (!passwordEncoder.matches(password, local.getPasswordHash())) {
                throw new RuntimeException("认证失败：账号或密码错误");
            }
            userContext = new UserContext();
            userContext.setAccount(local.getAccount());
            userContext.setName(local.getName());
            source = "LOCAL";
            needChangePwd = local.getMustChangePwd() != null && local.getMustChangePwd() == 1;
            log.info("[login] 本地用户认证成功，账号: {}", account);
        } else {
            // LDAP 同步用户落库即为有效账号，无需状态禁用校验
            log.info("[login] LDAP 用户认证成功，账号: {}", account);
        }

        // 生成 token
        String token = UUID.randomUUID().toString();

        // 角色与身份源
        userContext.setSource(source);
        List<SysRole> roles = getUserRoles(account);
        String primaryRole = getPrimaryRole(roles);
        userContext.setRole(primaryRole);
        userContext.setRoles(roles.stream().map(SysRole::getCode).collect(Collectors.toList()));

        // 从配置中获取 Redis Token 过期时间（单位：分钟）
        Long expireMinutes = configService.getRedisTokenExpireMinutes();
        // 将用户对象存储到 Redis
        redisUtil.setObject(token, userContext, expireMinutes, TimeUnit.MINUTES);

        log.info("[login] 登录成功，账号: {}, 姓名: {}, 来源: {}, 角色: {}, 是否需改密: {}, Token过期时间: {} 分钟",
                userContext.getAccount(), userContext.getName(), source, primaryRole, needChangePwd, expireMinutes);

        // 构建响应
        LoginResponse response = new LoginResponse();
        response.setToken(token);
        response.setAccount(userContext.getAccount());
        response.setName(userContext.getName());
        response.setRole(primaryRole);
        response.setRoles(userContext.getRoles());
        response.setSource(source);
        response.setNeedChangePwd(needChangePwd);

        return response;
    }

    /**
     * 获取用户角色列表（通过 sys_user_role 关联，多角色取并集）
     */
    public List<SysRole> getUserRoles(String account) {
        SysUser u = sysUserRepository.findByAccount(account).orElse(null);
        if (u == null) {
            return new ArrayList<>();
        }
        List<SysRole> roles = new ArrayList<>();
        for (SysUserRole ur : sysUserRoleRepository.findByUserId(u.getId())) {
            sysRoleRepository.findById(ur.getRoleId()).ifPresent(roles::add);
        }
        return roles;
    }

    /**
     * 取优先级最高（priority 数值最小）的角色 code，仅用于默认展示；无角色默认 user
     */
    public String getPrimaryRole(List<SysRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return "user";
        }
        return roles.stream()
                .min(Comparator.comparingInt(SysRole::getPriority))
                .map(SysRole::getCode)
                .orElse("user");
    }

    public void logout(String token) {
        UserContext userContext = redisUtil.getObject(token, UserContext.class);
        if (userContext == null) {
            log.info("[logout] token无效 {}", token);
        } else {
            log.info("[logout] 登出请求，token: {}, 账号: {}, 姓名: {}", token, userContext.getAccount(), userContext.getName());
            redisUtil.delete(token);
        }
        log.info("[logout] 登出成功，token已失效");
    }

    /**
     * 本地用户修改密码（首登改密或主动修改）
     * LDAP 用户不在 sys_user，无法使用此方法
     */
    public void changePassword(String account, String oldPassword, String newPassword) {
        SysUser local = sysUserRepository.findByAccount(account)
                .orElseThrow(() -> new RuntimeException("非本地用户，无法修改密码"));
        if (!passwordEncoder.matches(oldPassword, local.getPasswordHash())) {
            throw new RuntimeException("原密码错误");
        }
        local.setPasswordHash(passwordEncoder.encode(newPassword));
        local.setMustChangePwd(0);
        sysUserRepository.save(local);
        log.info("[changePassword] 本地用户密码修改成功，账号: {}", account);
    }
}
