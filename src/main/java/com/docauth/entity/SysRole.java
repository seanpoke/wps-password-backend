package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色定义表（与具体用户解耦，用户通过 sys_user_role 关联）
 * code 唯一，priority 越小优先级越高（0 最大），仅用于前端默认角色展示，不影响权限计算
 */
@Data
@Entity
@Table(name = "sys_role", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** 优先级，0 最大；数值越大优先级越低 */
    @Column(name = "priority", nullable = false)
    private Integer priority;

    @Column(name = "remark")
    private String remark;

    @Column(name = "create_time", insertable = false, updatable = false)
    private LocalDateTime createTime;
}
