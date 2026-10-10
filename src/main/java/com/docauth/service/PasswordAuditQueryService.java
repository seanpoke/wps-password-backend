package com.docauth.service;

import com.docauth.dto.PasswordAuditPage;
import com.docauth.dto.PasswordAuditQueryRequest;
import com.docauth.dto.PasswordAuditRecord;
import com.docauth.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class PasswordAuditQueryService {

    @Value("${password.log.dir:./passwordLog}")
    private String logDir;

    /**
     * 可选：ripgrep(rg) 可执行文件绝对路径。
     * 留空则自动探测：先查 PATH，再查 jar 同目录的 rg / rg.exe。
     * 提供 rg 后，查询改用 rg 做文件级过滤 + 计数（C/mmap，远快于逐行读），大数据量下必需。
     */
    @Value("${password.audit.rg.path:}")
    private String rgPathConfig;

    private static final String ACTIVE_FILE = "password_audit.log";
    /** 单次查询最多在内存中保留的记录数，防止超大日志撑爆堆 */
    private static final int MAX_LOAD = 500_000;
    /** rg 进程超时（秒） */
    private static final long RG_TIMEOUT_SECONDS = 60;
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private volatile String resolvedRg;
    private volatile boolean rgResolved;

    // ===================== 入口 =====================
    public PasswordAuditPage query(PasswordAuditQueryRequest req) {
        String uid = (req.getUid() == null) ? "" : req.getUid().trim();
        if (uid.isEmpty()) {
            throw new ApiException(400, "UID 为必填项");
        }
        LocalDateTime start = parseTs(req.getStartTime());
        LocalDateTime end = parseTs(req.getEndTime());
        // UID 与时间范围(起止日期)为必填：避免无约束地全盘扫描超大日志
        if (start == null || end == null) {
            throw new ApiException(400, "时间范围(开始/结束日期)为必填项");
        }
        if (end.isBefore(start)) {
            throw new ApiException(400, "结束日期不能早于开始日期");
        }
        // 结束日期若只精确到“日”，则视为当日 23:59:59.999，保证结束当天整天被纳入
        if (req.getEndTime() != null && req.getEndTime().trim().length() == 10) {
            end = end.withHour(23).withMinute(59).withSecond(59).withNano(999_000_000);
        }

        String rg = resolveRg();
        if (rg != null) {
            try {
                return queryWithRg(rg, req, start, end);
            } catch (Exception e) {
                log.error("[PasswordAuditQuery] rg 查询失败, 回退 Java 逐行读取: {}", e.getMessage());
            }
        }
        return queryJavaFallback(req, start, end);
    }

    // ===================== rg 快路径 =====================
    private PasswordAuditPage queryWithRg(String rg, PasswordAuditQueryRequest req,
                                          LocalDateTime start, LocalDateTime end)
            throws IOException, InterruptedException {
        List<Path> files = candidateFiles(req, start, end);
        if (files.isEmpty()) return emptyPage();

        List<String> cmd = buildRgCommand(rg, files, req);
        log.debug("[PasswordAuditQuery] rg cmd: {}", String.join(" ", cmd));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(false);
        Process p = pb.start();

        // 丢弃 stderr，避免子进程因输出管道满而阻塞
        drainAsync(p.getErrorStream());

        long total = 0;
        List<PasswordAuditRecord> loaded = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isEmpty()) continue;
                PasswordAuditRecord r = parseLine(line);
                if (r == null) continue;
                if (!inTimeRange(r.getTimestamp(), start, end)) continue;
                total++;
                if (loaded.size() < MAX_LOAD) loaded.add(r);
            }
        }

        boolean finished = p.waitFor(RG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new IOException("rg 执行超时(" + RG_TIMEOUT_SECONDS + "s)");
        }

        loaded.sort(Comparator.comparing(PasswordAuditRecord::getTimestamp).reversed());
        return paginate(loaded, total, req);
    }

    private List<String> buildRgCommand(String rg, List<Path> files, PasswordAuditQueryRequest req) {
        List<String> cmd = new ArrayList<>();
        cmd.add(rg);
        cmd.add("-P");             // PCRE2，支持环视 (?=...)
        cmd.add("-s");             // 全局大小写敏感（字段大小写由 (?i:...) 局部控制）
        cmd.add("--no-filename");  // 多文件时不打印文件名
        cmd.add("-N");             // 不打印行号
        cmd.add("--no-heading");
        cmd.add("--color"); cmd.add("never");
        cmd.add("-a");             // 全部按文本处理（防二进制误判）
        cmd.add("-e"); cmd.add(buildRegex(req));
        for (Path f : files) cmd.add(f.toString());
        return cmd;
    }

    /**
     * 组合各字段过滤条件为一条 PCRE2 正则（行首零宽环视），无过滤则匹配全部行。
     * 行结构：时间戳 | uid | path | before | after | possible | platform | createBy | keyVersion
     * 字段下标：uid=1 path=2 platform=6 createBy=7
     */
    private String buildRegex(PasswordAuditQueryRequest req) {
        StringBuilder look = new StringBuilder();
        if (isNotBlank(req.getUid())) {
            look.append(fieldContains(1, req.getUid().trim(), false));   // uid 大小写敏感
        }
        if (isNotBlank(req.getPath())) {
            look.append(fieldContains(2, req.getPath().trim(), true));   // path 大小写不敏感
        }
        if (isNotBlank(req.getUser())) {
            look.append(fieldContains(7, req.getUser().trim(), true));   // createBy 大小写不敏感
        }
        if (isNotBlank(req.getPlatform())) {
            look.append(fieldEquals(6, req.getPlatform().trim()));      // platform 大小写不敏感
        }
        return "^" + look + ".*";
    }

    /** 第 fieldIdx 个字段包含 value（字面量；ci=true 时该字段大小写不敏感） */
    private String fieldContains(int fieldIdx, String value, boolean ci) {
        StringBuilder sb = new StringBuilder("(?=[^|]*");
        for (int i = 0; i < fieldIdx; i++) sb.append("\\| [^|]*");
        if (ci) sb.append("(?i:");
        sb.append(escape(value));
        if (ci) sb.append(")");
        sb.append(")");
        return sb.toString();
    }

    /** 第 fieldIdx 个字段精确等于 value（大小写不敏感）。字段间分隔符为 " | "(前导空格+管+空格) */
    private String fieldEquals(int fieldIdx, String value) {
        StringBuilder sb = new StringBuilder("(?=[^|]*");
        for (int i = 0; i < fieldIdx; i++) sb.append("\\| [^|]*");
        // 末段 [^|]* 回溯到空，使 (?i:value) 落在字段起始；( \| |$) 处理字段后的 " | " 或行尾(前导空格)
        sb.append("(?i:").append(escape(value)).append(")( \\| |$))");
        return sb.toString();
    }

    /** 转义正则元字符，使用户输入按字面量匹配，杜绝正则注入 */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if ("\\.^$*+?()[]{}|".indexOf(c) >= 0) sb.append('\\');
            sb.append(c);
        }
        return sb.toString();
    }

    // ===================== Java 兜底路径 =====================
    private PasswordAuditPage queryJavaFallback(PasswordAuditQueryRequest req,
                                                LocalDateTime start, LocalDateTime end) {
        // 与 rg 快路径使用同一套“仅所选日期范围文件”的候选集，确保行为一致
        List<Path> files = candidateFiles(req, start, end);
        List<PasswordAuditRecord> all = new ArrayList<>();
        for (Path f : files) addFile(f, all);

        long total = 0;
        List<PasswordAuditRecord> loaded = new ArrayList<>();
        for (PasswordAuditRecord r : all) {
            if (!match(r, req)) continue;
            if (!inTimeRange(r.getTimestamp(), start, end)) continue;
            total++;
            if (loaded.size() < MAX_LOAD) loaded.add(r);
        }
        loaded.sort(Comparator.comparing(PasswordAuditRecord::getTimestamp).reversed());
        return paginate(loaded, total, req);
    }

    // ===================== 公共辅助 =====================
    /**
     * 仅返回落在 [start, end] 日期范围内的日志文件，杜绝无谓的全量扫描。
     * - 命名归档 2026_10_10.N.log 按文件名日期筛选；
     * - 当前活动文件 password_audit.log 仅当“今天”落在所选范围内才纳入
     *   （它承载今天尚未滚动的数据）。
     */
    private List<Path> candidateFiles(PasswordAuditQueryRequest req, LocalDateTime start, LocalDateTime end) {
        List<Path> files = new ArrayList<>();
        Path dir = Paths.get(logDir);
        if (!Files.isDirectory(dir)) return files;
        LocalDate startDay = start.toLocalDate();
        LocalDate endDay = end.toLocalDate();
        LocalDate today = LocalDate.now();

        Path active = dir.resolve(ACTIVE_FILE);
        if (Files.exists(active) && !today.isBefore(startDay) && !today.isAfter(endDay)) {
            files.add(active);
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.log")) {
            for (Path p : ds) {
                String name = p.getFileName().toString();
                if (ACTIVE_FILE.equals(name)) continue;
                LocalDateTime fd = extractDateFromName(name);
                if (fd == null) continue;
                LocalDate day = fd.toLocalDate();
                if (day.isBefore(startDay) || day.isAfter(endDay)) continue;
                files.add(p);
            }
        } catch (IOException e) {
            log.error("[PasswordAuditQuery] 列举归档失败: {}", e.getMessage(), e);
        }
        return files;
    }

    private PasswordAuditPage paginate(List<PasswordAuditRecord> loaded, long total,
                                       PasswordAuditQueryRequest req) {
        PasswordAuditPage page = new PasswordAuditPage();
        int size = Math.min(Math.max(1, req.getSize()), 200);
        int p = Math.max(1, req.getPage());
        int from = Math.min((p - 1) * size, loaded.size());
        int to = Math.min(from + size, loaded.size());
        page.setList(loaded.subList(from, to));
        page.setTotal(total);
        return page;
    }

    private PasswordAuditPage emptyPage() {
        PasswordAuditPage page = new PasswordAuditPage();
        page.setList(Collections.emptyList());
        page.setTotal(0);
        return page;
    }

    private void addFile(Path file, List<PasswordAuditRecord> out) {
        if (!Files.exists(file)) return;
        try (BufferedReader br = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                PasswordAuditRecord r = parseLine(line);
                if (r != null) out.add(r);
            }
        } catch (IOException e) {
            log.error("[PasswordAuditQuery] 读取日志失败 {}: {}", file, e.getMessage());
        }
    }

    // 行格式(编码器已加毫秒时间戳): 2026-10-10 11:45:36.123 | uid | path | before | after | possible | platform | createBy | keyVersion
    private PasswordAuditRecord parseLine(String line) {
        String[] f = line.split(" \\| ", -1);
        if (f.length < 9) return null;
        PasswordAuditRecord r = new PasswordAuditRecord();
        r.setTimestamp(f[0].trim());
        r.setUid(f[1].trim());
        r.setPath(f[2].trim());
        r.setBeforePassword(f[3].trim());
        r.setAfterPassword(f[4].trim());
        String possible = f[5].trim();
        r.setPossiblePasswords(possible.isEmpty() ? Collections.emptyList()
                : Arrays.asList(possible.split(",")));
        r.setPlatform(f[6].trim());
        r.setCreateBy(f[7].trim());
        r.setKeyVersion(f[8].trim());
        return r;
    }

    private boolean match(PasswordAuditRecord r, PasswordAuditQueryRequest req) {
        if (isNotBlank(req.getUid()) && !r.getUid().contains(req.getUid().trim())) return false;
        if (isNotBlank(req.getPath()) &&
            !r.getPath().toLowerCase().contains(req.getPath().trim().toLowerCase())) return false;
        if (isNotBlank(req.getUser()) &&
            !r.getCreateBy().toLowerCase().contains(req.getUser().trim().toLowerCase())) return false;
        if (isNotBlank(req.getPlatform()) &&
            !req.getPlatform().trim().equalsIgnoreCase(r.getPlatform())) return false;
        return true;
    }

    private boolean inTimeRange(String ts, LocalDateTime start, LocalDateTime end) {
        if (start == null && end == null) return true;
        LocalDateTime t = parseTimestamp(ts);
        if (t == null) return true; // 无法解析的时间戳不丢弃，交给上层
        if (start != null && t.isBefore(start)) return false;
        if (end != null && t.isAfter(end)) return false;
        return true;
    }

    private LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) return null;
        try {
            return LocalDateTime.parse(ts.trim(), TS_FMT);
        } catch (Exception e) {
            return null;
        }
    }

    // 兼容 2026_10_10.0.log / 2026_10.log / 2026_10_10_1145.0.log
    private LocalDateTime extractDateFromName(String name) {
        Matcher m = Pattern.compile("(\\d{4})_(\\d{2})(?:_(\\d{2}))?").matcher(name);
        if (m.find()) {
            try {
                int y = Integer.parseInt(m.group(1));
                int mo = Integer.parseInt(m.group(2));
                int d = m.group(3) != null ? Integer.parseInt(m.group(3)) : 1;
                return LocalDateTime.of(y, mo, d, 0, 0);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private LocalDateTime parseTs(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            if (s.length() == 10) s = s + " 00:00:00";
            return LocalDateTime.parse(s, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.trim().isEmpty();
    }

    // ===================== rg 路径探测 =====================
    private String resolveRg() {
        if (rgResolved) return resolvedRg;
        synchronized (this) {
            if (rgResolved) return resolvedRg;
            String found = doResolveRg();
            resolvedRg = found;
            rgResolved = true;
            if (found != null) {
                log.info("[PasswordAuditQuery] 使用 rg 加速查询: {}", found);
            } else {
                log.warn("[PasswordAuditQuery] 未找到 rg，回退 Java 逐行读取（大数据量下较慢）；" +
                        "可将 rg[rg.exe] 置于 PATH 或 jar 同目录，或用 password.audit.rg.path 指定");
            }
            return found;
        }
    }

    private String doResolveRg() {
        if (rgPathConfig != null && !rgPathConfig.isBlank() && Files.isRegularFile(Paths.get(rgPathConfig))) {
            return rgPathConfig;
        }
        String name = isWindows() ? "rg.exe" : "rg";
        // 1) PATH
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String d : pathEnv.split(File.pathSeparator)) {
                Path p = Paths.get(d, name);
                if (Files.isRegularFile(p)) return p.toString();
            }
        }
        // 2) jar 同目录（兼容 Spring Boot fat-jar 的 jar:file: 协议）
        String jarDir = jarDir();
        if (jarDir != null) {
            Path p = Paths.get(jarDir, name);
            if (Files.isRegularFile(p)) return p.toString();
        }
        // 3) 工作目录 / 工作目录下的 target（兜底，适配 local_deploy 从项目根启动）
        String wd = System.getProperty("user.dir");
        if (wd != null) {
            Path p1 = Paths.get(wd, name);
            if (Files.isRegularFile(p1)) return p1.toString();
            Path p2 = Paths.get(wd, "target", name);
            if (Files.isRegularFile(p2)) return p2.toString();
        }
        return null;
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private String jarDir() {
        try {
            java.net.URL loc = PasswordAuditQueryService.class.getProtectionDomain()
                    .getCodeSource().getLocation();
            // Spring Boot fat-jar: getLocation() = jar:file:/E:/.../x.jar!/BOOT-INF/classes/
            if ("jar".equals(loc.getProtocol())) {
                String s = loc.toString();
                int bang = s.indexOf("!/");
                if (bang > 0) {
                    String filePart = s.substring("jar:".length(), bang); // file:/E:/.../x.jar
                    try {
                        return new File(new java.net.URI(filePart)).getParent();
                    } catch (Exception e) {
                        return null;
                    }
                }
                return null;
            }
            File f = new File(loc.getPath());
            return f.isFile() ? f.getParent() : f.getPath();
        } catch (Exception e) {
            return null;
        }
    }

    private void drainAsync(InputStream is) {
        Thread t = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                while (r.readLine() != null) { /* 丢弃 */ }
            } catch (IOException ignored) {
            }
        }, "rg-stderr-drain");
        t.setDaemon(true);
        t.start();
    }
}
