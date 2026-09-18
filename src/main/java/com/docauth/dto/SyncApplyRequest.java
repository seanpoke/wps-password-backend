package com.docauth.dto;

import lombok.Data;

import java.util.List;

/**
 * LDAP 同步确认请求：管理员勾选后应用
 */
@Data
public class SyncApplyRequest {
    /** 勾选新增/更新的 LDAP 部门或用户 DN */
    private List<String> addDns;
    /** 勾选移除的部门 DB id（LDAP 已消失） */
    private List<Long> removeDeptIds;
    /** 勾选移除的用户 DB id */
    private List<Long> removeUserIds;
}
