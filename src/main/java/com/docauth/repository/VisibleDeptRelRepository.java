package com.docauth.repository;

import com.docauth.entity.VisibleDeptRel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VisibleDeptRelRepository extends JpaRepository<VisibleDeptRel, Long> {

    /** Query all visible departments bound to a given role/user. */
    List<VisibleDeptRel> findByRelTypeAndRelId(String relType, Long relId);

    /** Check whether a role/user already bound a department (for de-duplication). */
    Optional<VisibleDeptRel> findByRelTypeAndRelIdAndDeptId(String relType, Long relId, Long deptId);

    /** Reverse lookup: which roles/users set this department as visible. */
    List<VisibleDeptRel> findByDeptId(Long deptId);

    /** Cascade cleanup: remove all bindings of a role/user (batch DML). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VisibleDeptRel v where v.relType = :relType and v.relId = :relId")
    void deleteByRelTypeAndRelId(@Param("relType") String relType, @Param("relId") Long relId);

    /** Cascade cleanup: remove bindings when a department is deleted (batch DML). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VisibleDeptRel v where v.deptId = :deptId")
    void deleteByDeptId(@Param("deptId") Long deptId);

    /** 批量删除某部门相关的绑定（IN） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VisibleDeptRel v where v.deptId IN :ids")
    void deleteByDeptIds(@Param("ids") List<Long> ids);

    /** 批量删除某主体(USER/ROLE)相关的绑定（IN） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from VisibleDeptRel v where v.relType = :relType and v.relId IN :ids")
    void deleteByRelTypeAndRelIds(@Param("relType") String relType, @Param("ids") List<Long> ids);
}
