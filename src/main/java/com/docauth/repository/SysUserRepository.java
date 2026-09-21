package com.docauth.repository;

import com.docauth.entity.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRepository extends JpaRepository<SysUser, Long>, JpaSpecificationExecutor<SysUser> {

    Optional<SysUser> findByAccount(String account);

    boolean existsByAccount(String account);

    List<SysUser> findByDeptId(Long deptId);

    List<SysUser> findBySource(String source);

    /** 批量删除用户（IN，一条 SQL 覆盖多个 id） */
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from SysUser u where u.id in :ids")
    void deleteByIds(@org.springframework.data.repository.query.Param("ids") List<Long> ids);
}
