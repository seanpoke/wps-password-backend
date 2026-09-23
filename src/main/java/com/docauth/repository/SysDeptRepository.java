package com.docauth.repository;

import com.docauth.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findByParentId(Long parentId);

    List<SysDept> findByPathStartingWith(String pathPrefix);

    List<SysDept> findBySource(String source);

    Optional<SysDept> findByPathAndSource(String path, String source);

    /** 批量删除部门（IN，一条 SQL 覆盖多个 id） */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from SysDept d where d.id in :ids")
    void deleteByIds(@org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
