package com.docauth.repository;

import com.docauth.entity.AppVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 客户端版本 Repository（每平台多版本）
 */
@Repository
public interface AppVersionRepository extends JpaRepository<AppVersion, Long> {

    /** 列出某平台全部版本（按发布时间倒序，最新在前） */
    List<AppVersion> findByPlatformOrderByReleaseTimeDesc(String platform);

    /** 按平台 + 版本号查询唯一记录 */
    Optional<AppVersion> findByPlatformAndVersion(String platform, String version);

    /** 查询某平台被标记为最低支持版本的记录 */
    Optional<AppVersion> findByPlatformAndIsMinTrue(String platform);

    /** 查询某平台被标记为最新版本的记录 */
    Optional<AppVersion> findByPlatformAndIsLatestTrue(String platform);

    /** 取消某平台所有版本的最低标记 */
    @Modifying
    @Query("UPDATE AppVersion a SET a.isMin = false WHERE a.platform = :p")
    void unsetMin(@Param("p") String platform);

    /** 取消某平台所有版本的最新标记 */
    @Modifying
    @Query("UPDATE AppVersion a SET a.isLatest = false WHERE a.platform = :p")
    void unsetLatest(@Param("p") String platform);
}
