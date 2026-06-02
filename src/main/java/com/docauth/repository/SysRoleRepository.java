package com.docauth.repository;

import com.docauth.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    
    /**
     * 根据账号查询角色
     * @param account 账号
     * @return 角色信息
     */
    Optional<SysRole> findByAccount(String account);
}