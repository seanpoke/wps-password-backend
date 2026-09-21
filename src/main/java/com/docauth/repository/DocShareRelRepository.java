package com.docauth.repository;

import com.docauth.entity.DocShareRel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DocShareRelRepository extends JpaRepository<DocShareRel, Long> {
    List<DocShareRel> findByUid(String uid);

    DocShareRel findByUidAndTypeAndName(String uid, Integer type, String name);

    /** 将目标（部门/用户）相关的授权标记为失效（LDAP 移除或删除时保留审计） */
    @Modifying
    @Query("UPDATE DocShareRel r SET r.invalid = 1 WHERE r.targetId = :tid")
    void markInvalidByTargetId(@Param("tid") Long tid);

    /** 批量标记失效（IN，一条 SQL 覆盖多个目标） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DocShareRel r SET r.invalid = 1 WHERE r.targetId IN :ids")
    void markInvalidByTargetIds(@Param("ids") List<Long> ids);

    /** 物理删除某目标类型的授权关系（同步删除/更新时彻底清理） */
    void deleteByTargetIdAndType(@Param("tid") Long tid, @Param("type") int type);

    /** 统计某目标有效（未失效）的授权条数，type=0 表示部门 */
    @Query("SELECT COUNT(r) FROM DocShareRel r WHERE r.targetId = :tid AND r.type = :type AND r.invalid = 0")
    long countValidByTargetIdAndType(@Param("tid") Long tid, @Param("type") int type);
}
