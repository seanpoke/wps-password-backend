package com.docauth.repository;

import com.docauth.entity.DocInfo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocInfoRepository extends JpaRepository<DocInfo, Long> {
    DocInfo findByUid(String uid);
}