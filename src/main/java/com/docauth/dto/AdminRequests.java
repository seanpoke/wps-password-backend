package com.docauth.dto;

import com.docauth.entity.SysDept;
import com.docauth.entity.SysUser;
import com.docauth.entity.SysRole;
import com.docauth.entity.SysUserRole;

public class AdminRequests {

    public static class CreateDept {
        private String name;
        private Long parentId;
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long v) { parentId = v; }
    }

    public static class UpdateDept {
        private String name;
        private Long parentId; // 新父部门 id；null 表示改为顶级（仅 LOCAL 部门允许改父节点）
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long v) { parentId = v; }
    }

    public static class CreateUser {
        private String account;
        private String name;
        private String password;
        private Long deptId;
        private java.util.List<Long> visibleDeptIds; // 可见部门权限（按 USER 绑定）
        public String getAccount() { return account; }
        public void setAccount(String v) { account = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public String getPassword() { return password; }
        public void setPassword(String v) { password = v; }
        public Long getDeptId() { return deptId; }
        public void setDeptId(Long v) { deptId = v; }
        public java.util.List<Long> getVisibleDeptIds() { return visibleDeptIds; }
        public void setVisibleDeptIds(java.util.List<Long> v) { visibleDeptIds = v; }
    }

    public static class UpdateUser {
        private String name;
        private Long deptId;
        private java.util.List<Long> roleIds;
        private java.util.List<Long> visibleDeptIds; // 可见部门权限（按 USER 绑定）
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public Long getDeptId() { return deptId; }
        public void setDeptId(Long v) { deptId = v; }
        public java.util.List<Long> getRoleIds() { return roleIds; }
        public void setRoleIds(java.util.List<Long> v) { roleIds = v; }
        public java.util.List<Long> getVisibleDeptIds() { return visibleDeptIds; }
        public void setVisibleDeptIds(java.util.List<Long> v) { visibleDeptIds = v; }
    }

    public static class ResetPassword {
        private String newPassword;
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String v) { newPassword = v; }
    }

    public static class CreateRole {
        private String code;
        private String name;
        private Integer priority;
        private String remark;
        private java.util.List<Long> visibleDeptIds; // 可见部门权限（按 ROLE 绑定）
        public String getCode() { return code; }
        public void setCode(String v) { code = v; }
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer v) { priority = v; }
        public String getRemark() { return remark; }
        public void setRemark(String v) { remark = v; }
        public java.util.List<Long> getVisibleDeptIds() { return visibleDeptIds; }
        public void setVisibleDeptIds(java.util.List<Long> v) { visibleDeptIds = v; }
    }

    public static class UpdateRole {
        private String name;
        private Integer priority;
        private String remark;
        private java.util.List<Long> visibleDeptIds; // 可见部门权限（按 ROLE 绑定）
        public String getName() { return name; }
        public void setName(String v) { name = v; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer v) { priority = v; }
        public String getRemark() { return remark; }
        public void setRemark(String v) { remark = v; }
        public java.util.List<Long> getVisibleDeptIds() { return visibleDeptIds; }
        public void setVisibleDeptIds(java.util.List<Long> v) { visibleDeptIds = v; }
    }

    public static class BindUserRole {
        private String account;
        private Long roleId;
        public String getAccount() { return account; }
        public void setAccount(String v) { account = v; }
        public Long getRoleId() { return roleId; }
        public void setRoleId(Long v) { roleId = v; }
    }

}
