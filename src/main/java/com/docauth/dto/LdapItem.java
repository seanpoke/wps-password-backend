package com.docauth.dto;

import lombok.Data;
import java.util.List;

@Data
public class LdapItem {
    // 名称
    private String name;
    // 全路径
    private String fullPath;
    // 子部门列表
    private List<LdapItem> deptList;
    // 子员工列表
    private List<LdapItem> employeeList;
}