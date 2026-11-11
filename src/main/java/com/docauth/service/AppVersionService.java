package com.docauth.service;

import com.docauth.dto.AppVersionDto;
import com.docauth.dto.VersionCheckResponse;
import com.docauth.dto.VersionLatestVo;
import com.docauth.dto.VersionPageVo;
import com.docauth.dto.VersionUploadVo;
import com.docauth.entity.AppVersion;
import com.docauth.repository.AppVersionRepository;
import com.docauth.util.VersionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 客户端版本服务（每平台多版本 + 最低/最新标记）
 */
@Slf4j
@Service
public class AppVersionService {

    private static final Set<String> SUPPORTED_PLATFORMS = Set.of("win", "android");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private AppVersionRepository appVersionRepository;

    /** 下载地址公网前缀（见 application.yml 的 app.version.public-base-url） */
    @Value("${app.version.public-base-url:}")
    private String publicBaseUrl;

    /** 已发布安装包落盘目录（见 application.yml 的 app.version.file-dir） */
    @Value("${app.version.file-dir:./versionFile}")
    private String versionFileDir;

    /**
     * 暂存子目录名：固定在发布目录 versionFileDir 下（Docker 内 /app/versionFile/tmp）。
     * 与发布目录同一文件系统、共用一个 bind mount，发布时无需大文件复制；
     * 该路径由 WebMvcConfig 的资源解析器拒绝 /downloads/tmp/** 访问，未发布包不会外泄。
     * public 供 WebMvcConfig 共用，避免两处硬编码不一致。
     */
    public static final String STAGING_SUBDIR = "tmp";

    /** 暂存文件保留时长（小时），超时由定时任务清理 */
    @Value("${app.version.staging-ttl-hours:24}")
    private long stagingTtlHours;

    /** 各平台允许的安装包扩展名（防任意文件上传）：win 仅 zip，android 仅 apk */
    private static final Map<String, Set<String>> PLATFORM_ALLOWED_EXT = Map.of(
            "win", Set.of(".zip"),
            "android", Set.of(".apk"));

    /** 暂存文件命名分隔符：{uuid}__{platform}__{version}{ext} */
    private static final String STAGING_SEP = "__";

    /** tempId 严格限定为 UUID，防止通过 tempId 拼接路径穿越 */
    private static final Pattern TEMP_ID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

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
        resp.setDownloadUrl(resolveDownloadUrl(latest.getDownloadUrl()));
        resp.setChangelog(latest.getChangelog());
        resp.setReleaseTime(latest.getReleaseTime() == null ? null : latest.getReleaseTime().format(FORMATTER));
        return resp;
    }

    /**
     * 获取平台最新版本（免 token）：供客户端下载页展示最新版本号/下载地址/更新说明。
     * 复用 checkVersion 的最新版本定位与下载地址解析逻辑，但不做版本比较。
     */
    public VersionLatestVo getLatest(String platform) {
        validatePlatform(platform);
        AppVersion latest = appVersionRepository.findByPlatformAndIsLatestTrue(platform)
                .orElseThrow(() -> new RuntimeException("未配置平台[" + platform + "]的最新版本"));
        if (latest.getStatus() != null && latest.getStatus() != 1) {
            throw new RuntimeException("平台[" + platform + "]最新版本已停用");
        }
        VersionLatestVo vo = new VersionLatestVo();
        vo.setPlatform(latest.getPlatform());
        vo.setVersion(latest.getVersion());
        vo.setDownloadUrl(resolveDownloadUrl(latest.getDownloadUrl()));
        vo.setChangelog(latest.getChangelog());
        vo.setReleaseTime(latest.getReleaseTime() == null ? null : latest.getReleaseTime().format(FORMATTER));
        return vo;
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

        // 按版本号降序（语义化比较：1.10.0 > 1.2.0）；跨平台查询时先按平台分组
        dtos.sort((a, b) -> {
            if (!singlePlatform) {
                int c = a.getPlatform().compareTo(b.getPlatform());
                if (c != 0) return c;
            }
            return VersionUtil.compare(b.getVersion(), a.getVersion());
        });

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

    /** 新增一个版本记录（admin）：若携带 tempId，则先发布暂存安装包再入库 */
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
        // 顺序：先放好完整文件 → 再改 DB（DB 的 download_url 是唯一发布开关）。
        // 若 DB 写入失败，只留下无人引用的孤儿文件，不影响线上正在使用的包。
        av.setDownloadUrl(resolveUrlForSave(dto.getTempId(), dto.getDownloadUrl(),
                dto.getPlatform(), dto.getVersion()));
        av.setChangelog(dto.getChangelog());
        av.setStatus(1);
        av.setIsMin(false);
        av.setIsLatest(false);
        av.setReleaseTime(parseTime(dto.getReleaseTime()));
        appVersionRepository.save(av);
        // DB 已提交，暂存文件可安全丢弃；保存失败时保留暂存，用户可直接重试
        if (StringUtils.hasText(dto.getTempId())) {
            discardStaged(dto.getTempId().trim());
        }
        log.info("[AppVersionService] 平台[{}]新增版本: {}", dto.getPlatform(), av.getVersion());
    }

    /** 更新版本元数据（admin，版本号不可改）：携带 tempId 时才替换安装包 */
    public void updateVersion(Long id, AppVersionDto dto) {
        AppVersion av = appVersionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("版本记录不存在"));
        // 版本号不可改，归属校验以 DB 中的权威值为准（而不是请求体）
        av.setDownloadUrl(resolveUrlForSave(dto.getTempId(), dto.getDownloadUrl(),
                av.getPlatform(), av.getVersion()));
        av.setChangelog(dto.getChangelog());
        av.setReleaseTime(parseTime(dto.getReleaseTime()));
        appVersionRepository.save(av);
        if (StringUtils.hasText(dto.getTempId())) {
            discardStaged(dto.getTempId().trim());
        }
        log.info("[AppVersionService] 版本记录[{}]元数据已更新", id);
    }

    /**
     * 保存时解析最终下载地址：
     * - 有 tempId（重新上传并点了保存）→ 发布暂存文件，返回带内容摘要的新路径；
     * - 无 tempId → 沿用请求里的 downloadUrl（清空即「移除」，取消编辑时请求根本不会发出）。
     */
    private String resolveUrlForSave(String tempId, String downloadUrl, String platform, String version) {
        if (StringUtils.hasText(tempId)) {
            return publishStaged(tempId.trim(), platform, version);
        }
        return StringUtils.hasText(downloadUrl) ? downloadUrl : null;
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
        dto.setUpdateTime(av.getUpdateTime() == null ? null : av.getUpdateTime().format(FORMATTER));
        dto.setIsMin(av.getIsMin());
        dto.setIsLatest(av.getIsLatest());
        dto.setStatus(av.getStatus());
        return dto;
    }

    /**
     * 上传安装包（只暂存、不发布）：落到发布目录下的 tmp/ 子目录，文件名 {uuid}__{platform}__{version}{ext}。
     * tmp/ 被 WebMvcConfig 的资源解析器拒绝 /downloads/tmp/** 访问，且没有任何 DB 记录指向它，
     * 所以管理员点「保存」之前客户端下载的仍是旧包；点「取消」删掉暂存文件即可完全回滚。
     */
    public VersionUploadVo stagePackage(String platform, String version, MultipartFile file) {
        validatePlatform(platform);
        if (!VersionUtil.isValid(version)) {
            throw new RuntimeException("版本号格式不合法(应为 1 / 1.2 / 1.2.3)");
        }
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("上传文件为空");
        }
        String ext = extractExt(file.getOriginalFilename());
        String plat = platform.trim().toLowerCase();
        Set<String> allowedExt = PLATFORM_ALLOWED_EXT.getOrDefault(plat, Set.of(".zip", ".apk"));
        if (!allowedExt.contains(ext)) {
            throw new RuntimeException("平台 " + plat + " 不支持安装包类型: " + ext
                    + "，该平台允许: " + String.join(" ", allowedExt));
        }
        String ver = version.trim();
        String tempId = UUID.randomUUID().toString();
        try {
            Path staging = stagingDir();
            Files.createDirectories(staging);
            Path target = staging.resolve(tempId + STAGING_SEP + plat + STAGING_SEP + ver + ext).normalize();
            if (!target.startsWith(staging)) {
                throw new RuntimeException("非法文件路径");
            }
            // 不带 REPLACE_EXISTING：uuid 命名不会撞名，也绝不覆盖任何已有文件
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target);
            }
            VersionUploadVo vo = new VersionUploadVo();
            vo.setTempId(tempId);
            vo.setFileName(file.getOriginalFilename());
            vo.setSize(Files.size(target));
            log.info("[AppVersionService] 安装包已暂存(未发布): {}, tempId={}", target.getFileName(), tempId);
            return vo;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("安装包暂存失败: " + e.getMessage(), e);
        }
    }

    /** 丢弃暂存安装包（取消编辑 / 移除时调用）：幂等，已被定时清理时直接返回 */
    public void discardStaged(String tempId) {
        Path staged = findStaged(tempId, false);
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
            log.info("[AppVersionService] 暂存安装包已丢弃, tempId={}", tempId);
        } catch (IOException e) {
            // 删除失败（如 Windows 文件占用）不阻断取消流程，交给定时任务兜底
            log.warn("[AppVersionService] 暂存安装包删除失败, tempId={}: {}", tempId, e.getMessage());
        }
    }

    /**
     * 发布暂存包：正式文件名按内容摘要命名 PasswordManager-{version}-{sha256前16位}{ext}，
     * 永不覆盖已发布文件；只有 DB 的 download_url 改指向新文件后，客户端才会拿到新包。
     * 文件名变化也顺带解决了浏览器/CDN 缓存仍指向旧内容的问题。
     */
    private String publishStaged(String tempId, String platform, String version) {
        Path staged = findStaged(tempId, true);
        String plat = platform.trim().toLowerCase();
        String ver = version.trim();
        StagedMeta meta = parseStagedName(staged.getFileName().toString());
        if (!plat.equals(meta.platform) || !ver.equals(meta.version)) {
            throw new RuntimeException("暂存安装包与当前版本不匹配，请重新上传");
        }
        Set<String> allowedExt = PLATFORM_ALLOWED_EXT.getOrDefault(plat, Set.of(".zip", ".apk"));
        if (!allowedExt.contains(meta.ext)) {
            throw new RuntimeException("暂存安装包类型不合法: " + meta.ext);
        }
        String filename = "PasswordManager-" + ver + "-" + sha256Short(staged) + meta.ext;
        String dir = StringUtils.trimTrailingCharacter(versionFileDir, '/');
        try {
            Path base = Paths.get(dir).normalize();
            Files.createDirectories(base);
            Path target = base.resolve(filename).normalize();
            if (!target.startsWith(base)) {
                throw new RuntimeException("非法文件路径");
            }
            if (!Files.exists(target)) {
                // 暂存目录是发布目录的子目录（同一文件系统）：用硬链接瞬时生成 .part，
                // 不复制几百 MB 的包；再同目录原子改名，保证下载方永远看不到半个文件。
                // 硬链接不会移走 staged，DB 保存失败时 tempId 仍可重试。
                Path tmp = base.resolve("." + filename + "."
                        + UUID.randomUUID().toString().substring(0, 8) + ".part");
                try {
                    try {
                        Files.createLink(tmp, staged);
                    } catch (IOException | UnsupportedOperationException e) {
                        // 极少数文件系统不支持硬链接：退化为复制，语义不变
                        Files.deleteIfExists(tmp);
                        Files.copy(staged, tmp);
                    }
                    try {
                        Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException e) {
                        Files.move(tmp, target);
                    } catch (FileAlreadyExistsException e) {
                        // 并发下其它请求已放好同内容文件，复用即可
                    }
                } finally {
                    Files.deleteIfExists(tmp);
                }
            }
            // 暂存文件先保留：DB 保存成功后再由调用方删除，
            // 这样保存失败时 tempId 仍可用，用户可直接重试而无需重新上传。
            log.info("[AppVersionService] 暂存安装包已发布: {}, tempId={}", filename, tempId);
            return "/downloads/" + filename;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("安装包发布失败: " + e.getMessage(), e);
        }
    }

    /** 按 tempId 定位暂存文件；tempId 严格为 UUID，避免路径穿越。required=true 时找不到即报错 */
    private Path findStaged(String tempId, boolean required) {
        if (tempId == null || !TEMP_ID_PATTERN.matcher(tempId).matches()) {
            throw new RuntimeException("暂存ID不合法");
        }
        Path staging = stagingDir();
        if (!Files.isDirectory(staging)) {
            if (required) {
                throw new RuntimeException("暂存安装包不存在或已过期，请重新上传");
            }
            return null;
        }
        try (Stream<Path> stream = Files.list(staging)) {
            Path found = stream
                    .filter(p -> p.getFileName().toString().startsWith(tempId + STAGING_SEP))
                    .findFirst().orElse(null);
            if (found == null && required) {
                throw new RuntimeException("暂存安装包不存在或已过期，请重新上传");
            }
            return found;
        } catch (IOException e) {
            throw new RuntimeException("读取暂存安装包失败: " + e.getMessage(), e);
        }
    }

    /** 解析暂存文件名 {uuid}__{platform}__{version}{ext}，用于校验暂存包与目标版本是否匹配 */
    private StagedMeta parseStagedName(String name) {
        int first = name.indexOf(STAGING_SEP);
        int second = first < 0 ? -1 : name.indexOf(STAGING_SEP, first + STAGING_SEP.length());
        if (first < 0 || second < 0) {
            throw new RuntimeException("暂存文件名不合法");
        }
        String versionWithExt = name.substring(second + STAGING_SEP.length());
        String ext = extractExt(versionWithExt);
        if (!ext.startsWith(".") || versionWithExt.length() <= ext.length()) {
            throw new RuntimeException("暂存文件名不合法");
        }
        StagedMeta meta = new StagedMeta();
        meta.platform = name.substring(first + STAGING_SEP.length(), second);
        meta.version = versionWithExt.substring(0, versionWithExt.length() - ext.length());
        meta.ext = ext;
        return meta;
    }

    private static class StagedMeta {
        private String platform;
        private String version;
        private String ext;
    }

    /** 内容摘要（SHA-256 前 16 位 hex）：相同内容收敛到同一发布文件，不同内容永不互相覆盖 */
    private String sha256Short(Path file) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 16);
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("计算安装包摘要失败: " + e.getMessage(), e);
        }
    }

    /**
     * 兜底清理：用户直接关闭页面、断网或取消请求丢失时暂存文件不会被立即删除。
     * 发布目录只清理崩溃残留的 .part；Files.list 非递归，绝不碰 tmp/ 子目录里的暂存包。
     * 已被 DB 引用过的历史包一律保留作为回滚依据。
     * 注意：以后若新增「清理 DB 未引用的孤儿包」逻辑，必须显式排除 tmp/，
     * 否则会删掉管理员正在编辑、尚未保存的暂存包。
     */
    @Scheduled(fixedDelay = 1, initialDelay = 1, timeUnit = TimeUnit.HOURS)
    public void cleanStaging() {
        long ttlMs = Math.max(1L, stagingTtlHours) * 3600_000L;
        long now = System.currentTimeMillis();
        deleteExpired(stagingDir(), ttlMs, now, p -> true);
        deleteExpired(Paths.get(StringUtils.trimTrailingCharacter(versionFileDir, '/')).normalize(),
                ttlMs, now, p -> p.getFileName().toString().endsWith(".part"));
    }

    private void deleteExpired(Path dir, long ttlMs, long now, Predicate<Path> filter) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(p -> Files.isRegularFile(p)).filter(filter).forEach(p -> {
                try {
                    if (now - Files.getLastModifiedTime(p).toMillis() > ttlMs) {
                        Files.deleteIfExists(p);
                        log.info("[AppVersionService] 已清理过期临时文件: {}", p.getFileName());
                    }
                } catch (IOException e) {
                    log.warn("[AppVersionService] 清理临时文件失败: {} - {}", p.getFileName(), e.getMessage());
                }
            });
        } catch (IOException e) {
            log.warn("[AppVersionService] 扫描临时目录失败: {} - {}", dir, e.getMessage());
        }
    }

    /** 暂存目录 = 发布目录/tmp：与 file-dir 同源，共用一个 bind mount、同一文件系统 */
    private Path stagingDir() {
        return Paths.get(StringUtils.trimTrailingCharacter(versionFileDir, '/'), STAGING_SUBDIR).normalize();
    }

    /** 提取扩展名（兼容 .tar.gz） */
    private String extractExt(String name) {
        if (name == null) return "";
        String lower = name.toLowerCase();
        if (lower.endsWith(".tar.gz")) {
            return ".tar.gz";
        }
        int idx = lower.lastIndexOf('.');
        return idx < 0 ? "" : lower.substring(idx);
    }

    /**
     * 解析下载地址：已是完整 http(s) 链接则原样返回；
     * 否则视为相对路径（如 /downloads/win-1.0.2.exe），拼上公网前缀（若配置）。
     */
    private String resolveDownloadUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        if (publicBaseUrl == null || publicBaseUrl.isEmpty()) {
            return url;
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl : publicBaseUrl + "/";
        String path = url.startsWith("/") ? url.substring(1) : url;
        return base + path;
    }
}
