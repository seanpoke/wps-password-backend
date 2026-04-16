package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "doc_share_rel")
public class DocShareRel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "uid", nullable = false, length = 64)
    private String uid;

    @Column(name = "type", nullable = false)
    private Integer type;
    
    @Column(name = "name", nullable = false, length = 64)
    private String name;
    
    @Column(name = "dn", nullable = false, length = 255)
    private String dn;
    
    @Column(name = "create_time", nullable = false, updatable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date createTime;
    
    @Column(name = "create_by", nullable = false, length = 64)
    private String createBy;
    
    @PrePersist
    public void prePersist() {
        createTime = new Date();
    }
}