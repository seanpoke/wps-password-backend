package com.docauth.service;

import com.docauth.dto.AppVersionDto;
import com.docauth.dto.VersionCheckResponse;
import com.docauth.dto.VersionPageVo;
import com.docauth.entity.AppVersion;
import com.docauth.repository.AppVersionRepository;
import com.docauth.util.VersionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 客户端版本服务（每平台多版本 + 最低/最新标记）
 */
@Slf4j
@Service
public class AppVersionService {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("win", "android", "mac", "ios");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AppVersionRepository appVersionRepository;

    /**
     * 客户端版本检查（免 token）：按 platform + 当前版本判定更新类型
     * 强制更新语义：current < 最低支持版本 时 FORCE；把某版本同时设为最低+最新即实现「全员强制升级到该版本」。
     */
    public VersionCheckResponse checkVersion(String platform, String current) {
        validatePlatform(platform);
        if (!VersionUtil.isValid(current)) {
            throw new RuntimeException("当前版本号格式不合法");
        }
        AppVersion latest = appVersionRepository.findByPlatformAndIsLatestTrue(platform)
                .orElseThrow(() -> new RuntimeException("未配置平台[" + platform + "]的最新版本"));
        if (latest.getStatus() != null && latest.getStatus() != 1) {
            throw new RuntimeException("平台[" + platform + "]最新版本已停用");
        }
        AppVersion min = appVersionRepository.findByPlatformAndIsMinTrue(platform).orElse(null);

        int cmpLatest = VersionUtil.compare(current, latest.getVersion());
        String updateType;
        if (cmpLatest >= 0) {
            updateType = "NONE";
        } else if (min != null && VersionUtil.compare(current, min.getVersion()) < 0) {
            updateType = "FORCE";
        } else {
            updateType = "OPTIONAL";
        }

        VersionCheckResponse resp = new VersionCheckResponse();
        resp.setPlatform(platform);
        resp.setCurrentVersion(current);
        resp.setLatestVersion(latest.getVersion());
        resp.setMinVersion(min == null ? null : min.getVersion());
        resp.setUpdateType(updateType);
        resp.setDownloadUrl(latest.getDownloadUrl());
        resp.setChangelog(latest.getChangelog());
        resp.setReleaseTime(latest.getReleaseTime() == null ? null : latest.getReleaseTime().format(FORMATTER));
        return resp;
    }

    /**
     * 版本分页查询（admin）：platform 可选（不传查全平台）；status 可选
     * （latest/lowest/available/expired/unreleased/all）。派生状态与状态汇总由服务端计算，
     * 汇总不受状态筛选影响。
     */
    public VersionPageVo pageVersions(String platform, String status, int page, int size) {
        if (platform != null && !platform.isEmpty()) {
            validatePlatform(platform);
        }
        boolean singlePlatform = platform != null && !platform.isEmpty();
        List<AppVersion> rows = singlePlatform
                ? appVersionRepository.findByPlatformOrderByReleaseTimeDesc(platform)
                : appVersionRepository.findAll(org.springframework.data.domain.Sort.by(
                        org.springframework.data.domain.Sort.Direction.DESC, "releaseTime"));

        // 各平台最新/最低版本号，用于派生状态
        Map<String, String> latestMap = new HashMap<>();
        Map<String, String> minMap = new HashMap<>();
        for (AppVersion av : appVersionRepository.findAll()) {
            if (Boolean.TRUE.equals(av.getIsLatest())) {
                latestMap.put(av.getPlatform(), av.getVersion());
            }
            if (Boolean.TRUE.equals(av.getIsMin())) {
                minMap.put(av.getPlatform(), av.getVersion());
            }
        }

        List<AppVersionDto> dtos = rows.stream()
                .map(av -> {
                    AppVersionDto dto = toDto(av);
                    dto.setStates(deriveStates(av, latestMap.get(av.getPlatform()), minMap.get(av.getPlatform())));
                    return dto;
                })
                .collect(Collectors.toList());

        // 状态汇总（不受状态筛选影响）
        Map<String, Long> counts = new HashMap<>();
        counts.put("available", 0L);
        counts.put("expired", 0L);
        counts.put("unreleased", 0L);
        for (AppVersionDto d : dtos) {
            for (String st : d.getStates()) {
                counts.merge(st, 1L, Long::sum);
            }
        }

        // 状态筛选
        List<AppVersionDto> filtered = dtos;
        if (status != null && !status.isEmpty() && !"all".equalsIgnoreCase(status)) {
            filtered = dtos.stream()
                    .filter(d -> d.getStates() != null && d.getStates().contains(status))
                    .collect(Collectors.toList());
        }

        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min((safePage - 1) * safeSize, filtered.size());
        int to = Math.min(from + safeSize, filtered.size());

        VersionPageVo vo = new VersionPageVo(filtered.subList(from, to), filtered.size(), safePage, safeSize);
        vo.setScopeTotal(dtos.size());
        vo.setLatestVersion(singlePlatform ? latestMap.get(platform) : null);
        vo.setMinVersion(singlePlatform ? minMap.get(platform) : null);
        vo.setStatusCounts(counts);
        return vo;
    }

    /** 派生状态：同时标记最低+最新 -> 双状态；否则按与最新/最低版本号的比较得出 未发布/过期/可用 */
    private List<String> deriveStates(AppVersion av, String latest, String min) {
        List<String> states = new ArrayList<>();
        if (Boolean.TRUE.equals(av.getIsLatest())) {
            states.add("latest");
        }
        if (Boolean.TRUE.equals(av.getIsMin())) {
            states.add("lowest");
        }
        if (states.isEmpty()) {
            if (latest != null && VersionUtil.compare(av.getVersion(), latest) > 0) {
                states.add("unreleased");
            } else if (min != null && VersionUtil.compare(av.getVersion(), min) < 0) {
                states.add("expired");
            } else {
                states.add("available");
            }
        }
        return states;
    }

    /** 新增一个版本记录（admin） */
    public void createVersion(AppVersionDto dto) {
        validatePlatform(dto.getPlatform());
        if (!VersionUtil.isValid(dto.getVersion())) {
            throw new RuntimeException("版本号格式不合法(应为 1 / 1.2 / 1.2.3)");
        }
        if (appVersionRepository.findByPlatformAndVersion(dto.getPlatform(), dto.getVersion()).isPresent()) {
            throw new RuntimeException("平台[" + dto.getPlatform() + "]已存在版本 " + dto.getVersion());
        }
        AppVersion av = new AppVersion();
        av.setPlatform(dto.getPlatform());
        av.setVersion(dto.getVersion());
        av.setDownloadUrl(dto.getDownloadUrl());
        av.setChangelog(dto.getChangelog());
        av.setStatus(1);
        av.setIsMin(false);
        av.setIsLatest(false);
        av.setReleaseTime(parseTime(dto.getReleaseTime()));
        appVersionRepository.save(av);
        log.info("[AppVersionService] 平台[{}]新增版本: {}", dto.getPlatform(), av.getVersion());
    }

    /** 更新版本元数据（admin，版本号不可改） */
    public void updateVersion(Long id, AppVersionDto dto) {
        AppVersion av = appVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在"));
        av.setDownloadUrl(dto.getDownloadUrl());
        av.setChangelog(dto.getChangelog());
        av.setReleaseTime(parseTime(dto.getReleaseTime()));
        appVersionRepository.save(av);
        log.info("[AppVersionService] 版本记录[{}]元数据已更新", id);
    }

    /** 设为最低支持版本（admin）：自动取消同平台其它最低标记，并校验 最低 ≤ 最新 */
    @Transactional
    public void setMin(Long id) {
        AppVersion av = appVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在"));
        AppVersion latest = appVersionRepository.findByPlatformAndIsLatestTrue(av.getPlatform()).orElse(null);
        if (latest != null && VersionUtil.compare(latest.getVersion(), av.getVersion()) < 0) {
            throw new RuntimeException("最低支持版本不能高于最新版本（最新: " + latest.getVersion()
                    + ", 待设为最低: " + av.getVersion() + "）");
        }
        appVersionRepository.unsetMin(av.getPlatform());
        av.setIsMin(true);
        appVersionRepository.save(av);
        log.info("[AppVersionService] 平台[{}]版本[{}]设为最低支持版本", av.getPlatform(), av.getVersion());
    }

    /** 设为最新版本（admin）：自动取消同平台其它最新标记，并校验 最新 ≥ 最低 */
    @Transactional
    public void setLatest(Long id) {
        AppVersion av = appVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在"));
        AppVersion min = appVersionRepository.findByPlatformAndIsMinTrue(av.getPlatform()).orElse(null);
        if (min != null && VersionUtil.compare(av.getVersion(), min.getVersion()) < 0) {
            throw new RuntimeException("最新版本不能低于最低支持版本（最低: " + min.getVersion()
                    + ", 待设为最新: " + av.getVersion() + "）");
        }
        appVersionRepository.unsetLatest(av.getPlatform());
        av.setIsLatest(true);
        appVersionRepository.save(av);
        log.info("[AppVersionService] 平台[{}]版本[{}]设为最新版本", av.getPlatform(), av.getVersion());
    }

    /** 删除版本（admin）：当前最新版本不可删 */
    public void deleteVersion(Long id) {
        AppVersion av = appVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在"));
        if (Boolean.TRUE.equals(av.getIsLatest())) {
            throw new RuntimeException("不能删除当前最新版本，请先设置其它版本为最新");
        }
        appVersionRepository.delete(av);
        log.info("[AppVersionService] 平台[{}]版本[{}]已删除", av.getPlatform(), av.getVersion());
    }

    private LocalDateTime parseTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(time.trim(), FORMATTER);
        } catch (Exception e) {
            throw new RuntimeException("发布时间格式应为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private void validatePlatform(String platform) {
        if (platform == null || !SUPPORTED_PLATFORMS.contains(platform.trim().toLowerCase())) {
            throw new RuntimeException("不支持的平台: " + platform
                    + "，可选: " + String.join("/", SUPPORTED_PLATFORMS));
        }
    }

    private AppVersionDto toDto(AppVersion av) {
        AppVersionDto dto = new AppVersionDto();
        dto.setId(av.getId());
        dto.setPlatform(av.getPlatform());
        dto.setVersion(av.getVersion());
        dto.setDownloadUrl(av.getDownloadUrl());
        dto.setChangelog(av.getChangelog());
        dto.setReleaseTime(av.getReleaseTime() == null ? null : av.getReleaseTime().format(FORMATTER));
        dto.setIsMin(av.getIsMin());
        dto.setIsLatest(av.getIsLatest());
        dto.setStatus(av.getStatus());
        return dto;
    }
}
