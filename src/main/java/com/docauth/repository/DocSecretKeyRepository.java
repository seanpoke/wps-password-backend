package com.docauth.repository;

import com.docauth.entity.DocSecretKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DocSecretKeyRepository extends JpaRepository<DocSecretKey, Long> {
    
    /**
     * 根据uid和keyVersion查询文档密钥
     */
    Optional<DocSecretKey> findByUidAndKeyVersion(String uid, String keyVersion);

}
