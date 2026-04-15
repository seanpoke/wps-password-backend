package com.docauth.repository;

import com.docauth.entity.DocShareRel;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DocShareRelRepository extends JpaRepository<DocShareRel, Long> {
    List<DocShareRel> findByUid(String uid);
    DocShareRel findByUidAndRelTypeAndValue(String uid, Integer relType, String value);
}