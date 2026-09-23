package com.docauth.entity;

import jakarta.persistence.*;
import java.io.Serializable;

/**
 * Department visible scope (replaces the old "permission group" concept):
 * a user/role directly binds the departments (sys_dept.id) it can see.
 * rel_type=USER -> rel_id points to sys_user.id; rel_type=ROLE -> rel_id points to sys_role.id.
 */
@Entity
@Table(name = "visible_dept_rel", uniqueConstraints = @UniqueConstraint(columnNames = {"rel_type", "rel_id", "dept_id"}))
public class VisibleDeptRel implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rel_type", nullable = false, length = 16)
    private String relType;

    @Column(name = "rel_id", nullable = false)
    private Long relId;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    public VisibleDeptRel() {
    }

    public VisibleDeptRel(String relType, Long relId, Long deptId) {
        this.relType = relType;
        this.relId = relId;
        this.deptId = deptId;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRelType() {
        return relType;
    }

    public void setRelType(String relType) {
        this.relType = relType;
    }

    public Long getRelId() {
        return relId;
    }

    public void setRelId(Long relId) {
        this.relId = relId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }
}
