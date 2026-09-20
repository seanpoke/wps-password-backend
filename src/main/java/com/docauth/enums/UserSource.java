package com.docauth.enums;

/**
 * 用户/部门来源枚举（后端统一维护，前端下拉通过 /admin/users/sources 动态获取）
 */
public enum UserSource {

    /** 本地用户（管理员在后台创建，BCrypt 密码） */
    LOCAL("本地用户"),

    /** LDAP 同步用户（由 LDAP 组织树同步维护） */
    LDAP("LDAP 用户");

    private final String label;

    UserSource(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getCode() {
        return name();
    }

    /** 来源值是否为合法枚举 */
    public static boolean isValid(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (UserSource s : values()) {
            if (s.name().equals(code)) {
                return true;
            }
        }
        return false;
    }
}
