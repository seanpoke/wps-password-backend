package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.util.Date;

@Data
@Entity
@Table(name = "doc_password_log")
public class DocPasswordLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "uid", nullable = false, length = 64)
    private String uid;
    
    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "path", length = 500)
    private String path;

    @Column(name = "before_password", length = 255)
    private String beforePassword;

    @Column(name = "after_password", length = 255)
    private String afterPassword;

    @Column(name = "possible_password", columnDefinition = "text")
    private String possiblePassword;

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