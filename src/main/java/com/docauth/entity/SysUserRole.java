package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

/**
 * 用户-角色关联（多对多）
 */
@Data
@Entity
@Table(name = "sys_user_role", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "role_id"}))
public class SysUserRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;
}
