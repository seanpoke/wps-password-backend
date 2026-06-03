package com.docauth.repository;

import com.docauth.entity.ConfigSecretKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConfigSecretKeyRepository extends JpaRepository<ConfigSecretKey, Long> {

    /**
     * 根据keyVersion查询配置密钥
     */
    Optional<ConfigSecretKey> findByKeyVersion(String keyVersion);

    /**
     * 查询orderNum最大的配置密钥（优先级最高）
     */
    Optional<ConfigSecretKey> findFirstByOrderByOrderNumDesc();
}
