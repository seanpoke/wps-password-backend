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
    private long docAuthCount;

    public DeptRefVo(Long deptId, List<String> relLabels, long docAuthCount) {
        this.deptId = deptId;
        this.relLabels = relLabels;
        this.docAuthCount = docAuthCount;
    }

    public Long getDeptId() {
        return deptId;
    }

    public List<String> getRelLabels() {
        return relLabels;
    }

    public long getDocAuthCount() {
        return docAuthCount;
    }
}
