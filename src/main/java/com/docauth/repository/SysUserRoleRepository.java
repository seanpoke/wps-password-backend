package com.docauth.repository;

import com.docauth.entity.SysUserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysUserRoleRepository extends JpaRepository<SysUserRole, Long> {

    List<SysUserRole> findByUserId(Long userId);

    /** 批量查询（IN），一次取回多用户的所有角色绑定，避免逐用户查询 */
    List<SysUserRole> findByUserIdIn(List<Long> userIds);

    List<SysUserRole> findByRoleId(Long roleId);

    Optional<SysUserRole> findByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 批量 DML 删除，避免派生 delete 先把实体加载进持久化上下文再逐个 remove，
     * 从而规避 "Row was updated or deleted by another transaction" 异常。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SysUserRole u where u.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SysUserRole u where u.roleId = :roleId")
    void deleteByRoleId(@Param("roleId") Long roleId);

    /** 批量删除某用户相关的角色绑定（IN） */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from SysUserRole u where u.userId IN :ids")
    void deleteByUserIds(@Param("ids") List<Long> ids);
}
