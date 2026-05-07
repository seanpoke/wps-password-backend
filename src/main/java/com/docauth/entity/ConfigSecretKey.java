package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 配置密钥实体类
 * 用于存储多个版本的公私钥，order和keyVersion分别都是唯一的
 */
@Data
@Entity
@Table(name = "config_secret_key")
public class ConfigSecretKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "private_key", nullable = false, columnDefinition = "text")
    private String privateKey;

    @Column(name = "key_version", nullable = false, unique = true, length = 50)
    private String keyVersion;

    @Column(name = "order_num", nullable = false, unique = true)
    private Integer orderNum;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
