package com.docauth.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
public class DocShareRel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String uid;

    private Integer type;

    private String name;

    private String dn;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;

    private String createBy;
}
