package com.docauth.dto;

import lombok.Data;

/** 文档授权信息：type=0 部门 / type=1 用户 */
@Data
public class DocAuthVo {

    /** 0=部门 1=用户 */
    private Integer type;
    /** 部门名称或用户姓名 */
    private String name;
    /** type=1 时的用户账号 */
    private String account;

    public DocAuthVo(Integer type, String name, String account) {
        this.type = type;
        this.name = name;
        this.account = account;
    }
}
