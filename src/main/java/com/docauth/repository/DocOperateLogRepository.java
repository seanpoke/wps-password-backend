package com.docauth.repository;

import com.docauth.entity.DocOperateLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocOperateLogRepository extends JpaRepository<DocOperateLog, Long> {
}