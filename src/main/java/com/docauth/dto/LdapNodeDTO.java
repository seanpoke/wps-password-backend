package com.docauth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * LDAP 树节点精简DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LdapNodeDTO {

    /**
     * 区分名 (DN)
     */
    private String dn;

    /**
     * 类型: 0=部门, 1=用户
     */
    private Integer type;

    /**
     * 名称
     */
    private String name;

    /**
     * 账号 (sAMAccountName)
     */
    private String account;

    /**
     * 子部门列表 (仅部门节点有值)
     */
    private List<LdapNodeDTO> deptList;

    /**
     * 子员工列表 (仅部门节点有值)
     */
    private List<LdapNodeDTO> employList;

    /**
     * 是否有权限
     */
    private Boolean hasAuth;
}
