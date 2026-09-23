package com.docauth.repository;

import com.docauth.entity.DocInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface DocInfoRepository extends JpaRepository<DocInfo, Long>, JpaSpecificationExecutor<DocInfo> {
    DocInfo findByUid(String uid);
}
