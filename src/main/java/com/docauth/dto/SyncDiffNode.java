package com.docauth.dto;

import lombok.Data;

import java.util.List;

/**
 * LDAP 同步对比节点（合并 diff 树）
 */
@Data
public class SyncDiffNode {
    /** DB 行 id（GONE/CHANGED/SAME 时有值；NEW 为 null） */
    private Long id;
    /** LDAP DN */
    private String dn;
    /** 0 部门 / 1 用户 */
    private Integer type;
    private String name;
    private String account;
    /** NEW / GONE / CHANGED / SAME */
    private String status;
    private List<SyncDiffNode> children;
}
