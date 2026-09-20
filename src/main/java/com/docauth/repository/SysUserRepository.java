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
}
