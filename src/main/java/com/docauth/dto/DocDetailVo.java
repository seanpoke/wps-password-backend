package com.docauth.dto;

import com.docauth.entity.DocInfo;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 文档详情：基本信息 + 授权信息（部门/用户） */
@Data
public class DocDetailVo {

    private Long id;
    private String uid;
    private String fileName;
    private String account;
    private String ownerName;
    private LocalDateTime createTime;
    private String createBy;
    /** 授权列表（仅有效授权） */
    private List<DocAuthVo> auths;

    public static DocDetailVo of(DocInfo d, List<DocAuthVo> auths) {
        DocDetailVo vo = new DocDetailVo();
        vo.setId(d.getId());
        vo.setUid(d.getUid());
        vo.setFileName(d.getFileName());
        vo.setAccount(d.getAccount());
        vo.setOwnerName(d.getName());
        vo.setCreateTime(d.getCreateTime());
        vo.setCreateBy(d.getCreateBy());
        vo.setAuths(auths);
        return vo;
    }
}
