package com.docauth.repository;

import com.docauth.entity.DocPasswordLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocOperateLogRepository extends JpaRepository<DocPasswordLog, Long> {
}