package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配置密钥实体类
 * 用于存储多个版本的公私钥，order和keyVersion分别都是唯一的
 */
@Data
@Entity
public class ConfigSecretKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String publicKey;

    private String privateKey;

    private String keyVersion;

    private Integer orderNum;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;
}
