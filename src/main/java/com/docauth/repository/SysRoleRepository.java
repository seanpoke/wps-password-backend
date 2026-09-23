package com.docauth.repository;

import com.docauth.entity.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {

    Optional<SysRole> findByCode(String code);

    List<SysRole> findAllByOrderByPriorityAsc();

    boolean existsByCode(String code);
}
