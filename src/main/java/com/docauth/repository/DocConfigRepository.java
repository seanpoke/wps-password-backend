package com.docauth.repository;

import com.docauth.entity.DocConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置 Repository
 */
@Repository
public interface DocConfigRepository extends JpaRepository<DocConfig, Long> {

    /**
     * 根据类型和配置键查询配置(返回单个结果)
     *
     * @param type 配置类型
     * @param key 配置键
     * @return 配置项
     */
    Optional<DocConfig> findFirstByTypeAndKey(String type, String key);

    /**
     * 根据类型和配置键查询所有配置(用于 subTree 等多值配置)
     *
     * @param type 配置类型
     * @param key 配置键
     * @return 配置列表
     */
    List<DocConfig> findByTypeAndKey(String type, String key);

    /**
     * 根据类型查询所有配置
     *
     * @param type 配置类型
     * @return 配置列表
     */
    List<DocConfig> findByType(String type);
}
