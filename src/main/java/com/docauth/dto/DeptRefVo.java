package com.docauth.dto;

import java.util.List;

/**
 * Reverse-reference info for a department: which roles/users directly set it as visible scope
 * (relLabels), plus how many document authorizations it carries.
 * Used by the frontend to show a lightweight badge on the department tree and to warn on delete.
 */
public class DeptRefVo {

    private Long deptId;
    private List<String> relLabels;
    /** 按类型分组的引用：设为可见的用户账号 / 角色名 */
    private List<String> userLabels;
    private List<String> roleLabels;
    private long docAuthCount;

    public DeptRefVo(Long deptId, List<String> relLabels, List<String> userLabels, List<String> roleLabels, long docAuthCount) {
        this.deptId = deptId;
        this.relLabels = relLabels;
        this.userLabels = userLabels;
        this.roleLabels = roleLabels;
        this.docAuthCount = docAuthCount;
    }

    public Long getDeptId() {
        return deptId;
    }

    public List<String> getRelLabels() {
        return relLabels;
    }

    public List<String> getUserLabels() {
        return userLabels;
    }

    public List<String> getRoleLabels() {
        return roleLabels;
    }

    public long getDocAuthCount() {
        return docAuthCount;
    }
}
