package com.docauth.dto;

import com.docauth.entity.DocInfo;
import lombok.Data;

import java.time.LocalDateTime;

/** 文档管理分页列表行 */
@Data
public class DocPageVo {

    private Long id;
    private String uid;
    private String fileName;
    private String account;
    private String ownerName;
    private LocalDateTime createTime;

    public DocPageVo(Long id, String uid, String fileName, String account, String ownerName, LocalDateTime createTime) {
        this.id = id;
        this.uid = uid;
        this.fileName = fileName;
        this.account = account;
        this.ownerName = ownerName;
        this.createTime = createTime;
    }

    public static DocPageVo of(DocInfo d) {
        return new DocPageVo(d.getId(), d.getUid(), d.getFileName(), d.getAccount(), d.getName(), d.getCreateTime());
    }
}
