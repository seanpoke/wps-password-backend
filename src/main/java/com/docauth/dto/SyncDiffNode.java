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

    /** 变更明细（仅 CHANGED 有值）：field 中文标签，oldVal/newVal 分别为本地原值与 LDAP 新值 */
    private List<ChangeItem> changes;

    /** 单条变更（名称/上级部门/姓名/所属部门） */
    @Data
    public static class ChangeItem {
        private String field;
        private String oldVal;
        private String newVal;

        public ChangeItem() {}

        public ChangeItem(String field, String oldVal, String newVal) {
            this.field = field;
            this.oldVal = oldVal;
            this.newVal = newVal;
        }
    }
}
