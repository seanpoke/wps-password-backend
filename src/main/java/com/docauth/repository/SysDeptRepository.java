package com.docauth.repository;

import com.docauth.entity.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

import java.util.List;

@Repository
public interface SysDeptRepository extends JpaRepository<SysDept, Long> {

    List<SysDept> findByParentId(Long parentId);

    List<SysDept> findByPathStartingWith(String pathPrefix);

    List<SysDept> findBySource(String source);

    Optional<SysDept> findByPathAndSource(String path, String source);
}
