package com.docauth.dto;

import java.util.List;

/** 用户管理分页行 VO（含部门名称、角色列表） */
public class UserPageVo {
    private Long id;
    private String account;
    private String name;
    private Long deptId;
    private String deptName;
    private String source;
    private Integer mustChangePwd;
    private java.time.LocalDateTime updateTime;
    private List<RoleItem> roles;

    public static class RoleItem {
        private Long id;
        private String code;
        private String name;

        public RoleItem(Long id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }

        public Long getId() { return id; }
        public void setId(Long v) { id = v; }
        public String getCode() { return code; }
        public void setCode(String v) { code = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
    }

    public Long getId() { return id; }
    public void setId(Long v) { id = v; }
    public String getAccount() { return account; }
    public void setAccount(String v) { account = v; }
    public String getName() { return name; }
    public void setName(String v) { name = v; }
    public Long getDeptId() { return deptId; }
    public void setDeptId(Long v) { deptId = v; }
    public String getDeptName() { return deptName; }
    public void setDeptName(String v) { deptName = v; }
    public String getSource() { return source; }
    public void setSource(String v) { source = v; }
    public Integer getMustChangePwd() { return mustChangePwd; }
    public void setMustChangePwd(Integer v) { mustChangePwd = v; }
    public java.time.LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(java.time.LocalDateTime v) { updateTime = v; }
    public List<RoleItem> getRoles() { return roles; }
    public void setRoles(List<RoleItem> v) { roles = v; }
}
