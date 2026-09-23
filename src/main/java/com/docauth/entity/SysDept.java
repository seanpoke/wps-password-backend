package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 本地部门表（支持外部三方多级部门）
 * path 采用 DN 风格逗号串，与 LDAP DN 共用 isDnSubPath 判定逻辑
 */
@Data
@Entity
@Table(name = "sys_dept")
public class SysDept {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long parentId;

    private String name;

    private String path;

    private String source;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;
}
