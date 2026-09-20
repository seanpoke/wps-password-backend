package com.docauth.dto;

import java.util.List;

/**
 * 角色列表视图对象：在角色基础字段上附带“可见部门权限”名称列表。
 * deptId=0 在列表中显示为“全部”。
 */
public class RoleListVo {
    private Long id;
    private String code;
    private String name;
    private Integer priority;
    private String remark;
    private List<String> visibleDeptNames; // “全部” 或 具体部门名

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getCode() { return code; }
    public void setCode(String v) { code = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer v) { priority = v; }
    public String getRemark() { return remark; }
    public void setRemark(String v) { remark = v; }
    public List<String> getVisibleDeptNames() { return visibleDeptNames; }
    public void setVisibleDeptNames(List<String> v) { visibleDeptNames = v; }
}
