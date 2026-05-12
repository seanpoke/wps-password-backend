package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;


/**
 * 系统配置实体类
 */
@Data
@Entity
public class DocConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String type;

    private String key;

    private String value;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
