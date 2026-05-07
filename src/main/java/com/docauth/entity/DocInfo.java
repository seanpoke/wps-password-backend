package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "doc_info",
        indexes = {
                @Index(name = "idx_uid", columnList = "uid", unique = true)
        })
public class DocInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "uid", unique = true, nullable = false, length = 64)
    private String uid;
    
    @Column(name = "account", nullable = false, length = 64)
    private String account;

    @Column(name = "name", nullable = false, length = 64)
    private String name;
    
    @Column(name = "create_time", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "create_by", nullable = false, length = 64)
    private String createBy;
    
    @Column(name = "update_time")
    @Temporal(TemporalType.TIMESTAMP)
    private Date updateTime;
    
    @Column(name = "update_by", length = 64)
    private String updateBy;
    
    @PrePersist
    public void prePersist() {
        createTime = new Date();
        updateTime = new Date();
    }
    
    @PreUpdate
    public void preUpdate() {
        updateTime = new Date();
    }
}