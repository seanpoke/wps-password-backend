package com.docauth.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 外部三方用户表（本地账号 + BCrypt 密码）
 * 内部 LDAP 用户不落此表，仅通过 user_scope_group.account 字符串绑定部门可见范围
 */
@Data
@Entity
@Table(name = "sys_user")
public class SysUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String account;

    private String name;

    @JsonIgnore
    private String passwordHash;

    private Long deptId;

    private Integer mustChangePwd;

    private String source;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
