package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
public class DocInfo {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String uid;
    
    private String account;

    private String name;
    
    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;
    
    private String createBy;
    
    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
    
    private String updateBy;
}