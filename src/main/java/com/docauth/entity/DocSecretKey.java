package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 文档密钥实体类
 * 用于存储同一个uid可能对应的多个版本的公私钥
 */
@Data
@Entity
@Table(name = "doc_secret_key")
public class DocSecretKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "uid", nullable = false, length = 64)
    private String uid;

    @Column(name = "public_key", nullable = false, columnDefinition = "text")
    private String publicKey;

    @Column(name = "private_key", nullable = false, columnDefinition = "text")
    private String privateKey;

    @Column(name = "key_version", nullable = false, length = 50)
    private String keyVersion;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;
}
