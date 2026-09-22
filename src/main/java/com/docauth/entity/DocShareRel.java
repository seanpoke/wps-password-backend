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

    /** id 化授权目标：type=0 -> sys_dept.id；type=1 -> sys_user.id */
    private Long targetId;

    /** 0=有效，1=已失效（被删除的部门/用户授权），运行期忽略 */
    @Column(nullable = false, columnDefinition = "tinyint(1) default 0")
    private Integer invalid = 0;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createTime;

    private String createBy;
}
