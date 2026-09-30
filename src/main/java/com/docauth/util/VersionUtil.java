package com.docauth.util;

/**
 * 语义化版本号比较工具
 */
public class VersionUtil {

    /**
     * 比较语义化版本号 a 与 b（格式 major[.minor[.patch[.build]]]，各段可不等长）。
     *
     * @return 负数: a < b；0: 相等；正数: a > b。空串按 0 处理。
     */
    public static int compare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        String[] as = a.trim().split("\\.");
        String[] bs = b.trim().split("\\.");
        int len = Math.max(as.length, bs.length);
        for (int i = 0; i < len; i++) {
            int ai = i < as.length ? parse(as[i]) : 0;
            int bi = i < bs.length ? parse(bs[i]) : 0;
            if (ai != bi) {
                return Integer.compare(ai, bi);
            }
        }
        return 0;
    }

    /**
     * 校验版本号格式：1 / 1.2 / 1.2.3 / 1.2.3.4
     */
    public static boolean isValid(String v) {
        if (v == null || v.trim().isEmpty()) {
            return false;
        }
        return v.trim().matches("^\\d+(\\.\\d+){0,3}$");
    }

    private static int parse(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
