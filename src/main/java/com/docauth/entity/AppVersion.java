package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户端版本实体（每平台多个版本，通过 is_min / is_latest 标记最低与最新）
 */
@Data
@Entity
@Table(name = "app_version")
public class AppVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "platform", nullable = false, length = 20)
    private String platform;

    @Column(name = "ver", nullable = false, length = 32)
    private String version;

    @Column(name = "download_url", length = 512)
    private String downloadUrl;

    @Column(name = "changelog", columnDefinition = "TEXT")
    private String changelog;

    @Column(name = "release_time")
    private LocalDateTime releaseTime;

    @Column(name = "status", nullable = false)
    private Integer status = 1;

    @Column(name = "is_min", nullable = false)
    private Boolean isMin = false;

    @Column(name = "is_latest", nullable = false)
    private Boolean isLatest = false;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(insertable = false, updatable = false)
    private LocalDateTime updateTime;
}
