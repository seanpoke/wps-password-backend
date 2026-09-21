package com.docauth.service;

import com.docauth.context.UserContext;
import com.docauth.dto.LdapNodeDTO;
import com.docauth.entity.DocShareRel;
import com.docauth.repository.DocShareRelRepository;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.HardcodedFilter;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.query.SearchScope;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class LdapService {

    /**
     * 部门节点内存缓存：按 来源+根节点 分组缓存
     * key = source + ":" + rootDn.toLowerCase()（如 "LDAP:ou=org,dc=x"）；
     * value = 该根节点下的整棵部门/用户树及过期时间
     */
    private final ConcurrentHashMap<String, CacheEntry> deptNodeCache = new ConcurrentHashMap<>();
    @Autowired
    private LdapTemplate ldapTemplate;
    @Autowired
    private DocShareRelRepository docShareRelRepository;
    @Autowired
    private ConfigService configService;

    /**
     * 验证用户身份并返回用户上下文
     *
     * @param account  用户名（sAMAccountName）
     * @param password 密码
     * @return 用户上下文对象，如果认证失败则返回 null
     */
    public UserContext authenticate(String account, String password) {
        try {
            log.info("开始 LDAP 认证，账号: {}", account);

            // 构建 LDAP 查询
            var query = LdapQueryBuilder.query()
                    .base(configService.getLdapBase())
                    .where("sAMAccountName").is(account);

            // 尝试验证密码
            ldapTemplate.authenticate(query, password);

            // 认证成功，查询用户详细信息
            log.info("LDAP 认证成功，正在获取用户信息: {}", account);

            List<UserContext> results = ldapTemplate.search(
                    query,
                    (AttributesMapper<UserContext>) attrs -> {
                        UserContext userContext = new UserContext();

                        // 设置账号
                        userContext.setAccount(account);

                        // 获取用户名称（优先 cn，其次 displayName）
                        String name = null;
                        if (attrs.get("cn") != null) {
                            name = attrs.get("cn").get().toString();
                        } else if (attrs.get("displayName") != null) {
                            name = attrs.get("displayName").get().toString();
                        }
                        userContext.setName(name != null ? name : account);

                        log.info("获取到用户信息 - 账号: {}, 姓名: {}", account, userContext.getName());
                        return userContext;
                    }
            );

            if (results != null && !results.isEmpty()) {
                return results.get(0);
            }

            // 如果查询失败，返回基本的用户上下文
            log.warn("无法获取用户详细信息，使用默认名称");
            UserContext fallbackContext = new UserContext();
            fallbackContext.setAccount(account);
            fallbackContext.setName(account);
            return fallbackContext;

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            // 账号在 LDAP 中不存在（如本地外部用户），属正常分支，回退本地认证，不刷 ERROR 堆栈
            log.debug("LDAP 未找到账号 {}，将回退本地用户认证", account);
            return null;
        } catch (Exception e) {
            log.error("LDAP 认证异常: {}, 原因: {}", account, e.getMessage(), e);
            return null;
        }
    }

    /**
     * 查询 LDAP 树形结构并标记权限
     *
     * @param docId 文档ID，如果为空则不标记权限
     * @return LDAP 树形结构列表
     */
    public List<LdapNodeDTO> getLdapTreeWithAuth(String docId) {
        // 如果提供了docId，先查询该文档的所有授权记录（只查一次数据库）
        List<DocShareRel> shareRels = null;
        if (docId != null) {
            shareRels = docShareRelRepository.findByUid(docId);
            log.info("查询到文档 {} 的授权记录数量: {}", docId, shareRels != null ? shareRels.size() : 0);
        }

        // 调试日志：打印配置的子树
        log.info("配置的 baseDn: {}", configService.getLdapBase());
        log.info("配置的 subTrees: {}", configService.getLdapTrees());

        // 获取 LDAP 树形结构(从缓存或LDAP查询)
        List<LdapTreeNode> tree = getLdapTreeNodes();
        log.info("获取到 LDAP 树根节点数量: {}", tree.size());

        // 转换为精简的 DTO
        List<LdapNodeDTO> result = new ArrayList<>();
        for (LdapTreeNode node : tree) {
            LdapNodeDTO dto = toDTO(node);
            // 递归标记权限
            if (!CollectionUtils.isEmpty(shareRels)) {
                markAuthStatus(dto, shareRels);
            }
            result.add(dto);
        }
        return result;
    }

    private List<LdapTreeNode> getLdapTreeNodes() {
        // 根节点集合：baseDn + 配置的子树（subTrees），均按来源=LDAP 分组缓存
        List<String> roots = new ArrayList<>();
        roots.add(configService.getLdapBase().toLowerCase());
        List<String> subTrees = configService.getLdapTrees();
        if (subTrees != null) {
            for (String s : subTrees) roots.add(s.toLowerCase());
        }

        // 1. 优先从本地内存缓存按根分组查询（全部命中且未过期才走缓存）
        List<LdapTreeNode> cached = new ArrayList<>();
        boolean allHit = true;
        for (String root : roots) {
            CacheEntry entry = deptNodeCache.get("LDAP:" + root);
            if (entry == null || entry.isExpired()) {
                allHit = false;
                break;
            }
            cached.addAll(entry.getNodes());
        }
        if (allHit) {
            log.info("从本地内存缓存获取 LDAP 树（按来源+根分组），根数量: {}", roots.size());
            return cached;
        }

        // 2. 缓存未全部命中/已过期，访问 LDAP 查询
        log.info("本地缓存未全部命中或已过期，开始查询 LDAP 树形结构, base: {}", configService.getLdapBase());

        // 从LDAP查询平铺的节点列表
        List<LdapTreeNode> allEntries = getLdapEntriesFromLdap();
        log.info("从LDAP查询到节点数量: {}", allEntries.size());

        // 构建树形结构（buildTree 已按子树范围裁剪，返回各根节点）
        List<LdapTreeNode> tree = buildTree(allEntries);
        log.info("构建后的树根节点数量: {}", tree.size());

        // 3. 将构建好的树按根拆分存入本地内存缓存（从数据库读取过期时间）
        long ttlMillis = configService.getCacheExpireMinutes() * 60 * 1000L; // 转换为毫秒
        for (LdapTreeNode rootNode : tree) {
            String key = "LDAP:" + rootNode.getDn().toLowerCase();
            List<LdapTreeNode> single = new ArrayList<>();
            single.add(rootNode);
            deptNodeCache.put(key, new CacheEntry(single, ttlMillis));
            log.info("LDAP 树形结构已缓存到本地内存, key: {}, 过期时间: {}分钟",
                    key, configService.getCacheExpireMinutes());
        }

        return tree;
    }

    /**
     * 递归标记节点的权限状态
     * hasAuth 仅表示该节点本身在数据库中存在授权记录，不从父节点继承权限
     *
     * @param node      节点
     * @param shareRels 授权记录列表（已预先查询）
     */
    private void markAuthStatus(LdapNodeDTO node, List<DocShareRel> shareRels) {
        // 判断当前节点是否有权限（仅检查服务端数据中是否存在精确匹配）
        boolean hasAuth = false;
        if (shareRels != null && !shareRels.isEmpty()) {
            for (DocShareRel rel : shareRels) {
                // id 化匹配：授权记录的 (type, targetId) 与节点 (type, id) 精确对应
                if (rel.getType() != null && rel.getType().equals(node.getType())
                        && rel.getTargetId() != null && rel.getTargetId().equals(node.getId())) {
                    hasAuth = true;
                    break;
                }
            }
        }

        node.setHasAuth(hasAuth);

        // 递归处理子部门
        if (node.getDeptList() != null) {
            for (LdapNodeDTO child : node.getDeptList()) {
                markAuthStatus(child, shareRels);
            }
        }

        // 递归处理子员工
        if (node.getEmployList() != null) {
            for (LdapNodeDTO child : node.getEmployList()) {
                markAuthStatus(child, shareRels);
            }
        }
    }

    /**
     * 转换单个节点为 DTO
     *
     * @param node 完整节点
     * @return 精简 DTO
     */
    private LdapNodeDTO toDTO(LdapTreeNode node) {
        LdapNodeDTO dto = new LdapNodeDTO();
        dto.setDn(node.getDn());

        // 从 attributes 中获取 name
        String name = (String) node.getAttributes().get("name");
        dto.setName(name != null ? name : node.getName());

        // 从 attributes 中获取 sAMAccountName
        String sAMAccountName = (String) node.getAttributes().get("sAMAccountName");

        // 根据 sAMAccountName 判断类型
        if (sAMAccountName != null && !sAMAccountName.isEmpty()) {
            dto.setType(1); // 用户
            dto.setAccount(sAMAccountName);
        } else {
            dto.setType(0); // 部门
            dto.setAccount(null);
        }

        // 初始化 hasAuth 为 false
        dto.setHasAuth(false);

        // 递归转换子节点并按类型分类
        if (node.getChildren() != null && !node.getChildren().isEmpty()) {
            List<LdapNodeDTO> deptList = new ArrayList<>();
            List<LdapNodeDTO> employList = new ArrayList<>();

            for (LdapTreeNode child : node.getChildren()) {
                LdapNodeDTO childDTO = toDTO(child);
                // 根据子节点类型分类
                if (childDTO.getType() == 0) {
                    deptList.add(childDTO); // 子部门
                } else {
                    employList.add(childDTO); // 子员工
                }
            }

            dto.setDeptList(deptList.isEmpty() ? null : deptList);
            dto.setEmployList(employList.isEmpty() ? null : employList);
        }

        return dto;
    }

    /**
     * 构建 LDAP 树形结构（仅包含配置的子树）
     *
     * @param allNodes 所有 LDAP 节点
     * @return 树形结构列表
     */
    private List<LdapTreeNode> buildTree(List<LdapTreeNode> allNodes) {
        List<LdapTreeNode> roots = new ArrayList<>();
        Map<String, LdapTreeNode> dnMap = new HashMap<>();

        // 创建 DN 到节点的映射（过滤掉没有 DN 的节点）
        for (LdapTreeNode node : allNodes) {
            if (node.getDn() != null && !node.getDn().isEmpty()) {
                dnMap.put(node.getDn().toLowerCase(), node);
            }
        }

        // 构建树形结构
        for (LdapTreeNode node : allNodes) {
            // 跳过没有 DN 的节点
            if (node.getDn() == null || node.getDn().isEmpty()) {
                continue;
            }

            String dn = node.getDn().toLowerCase();

            // 检查该节点是否属于配置的子树范围
            if (!isInSubTree(dn)) {
                continue; // 跳过不在配置子树范围内的节点
            }

            // 解析父 DN
            String parentDn = getParentDn(dn);

            if (parentDn == null || parentDn.isEmpty()) {
                // 根节点
                roots.add(node);
            } else {
                // 查找父节点并添加为子节点
                LdapTreeNode parentNode = dnMap.get(parentDn.toLowerCase());
                if (parentNode != null) {
                    // 只有当父节点也在子树范围内时才添加
                    if (isInSubTree(parentDn.toLowerCase())) {
                        parentNode.addChild(node);
                    } else {
                        // 如果父节点不在子树范围内，则当前节点作为根节点
                        roots.add(node);
                    }
                } else {
                    // 如果找不到父节点，也作为根节点
                    roots.add(node);
                }
            }
        }

        return roots;
    }

    /**
     * 判断节点是否在配置的子树范围内
     *
     * @param dn 节点的 DN(小写)
     * @return 是否在子树范围内
     */
    private boolean isInSubTree(String dn) {
        if (dn == null || dn.isEmpty()) {
            return false;
        }

        String lowerDn = dn.toLowerCase();
        String lowerBase = configService.getLdapBase().toLowerCase();

        // baseDn 本身始终包含
        if (lowerDn.equals(lowerBase)) {
            return true;
        }

        // 如果配置了子树限制,检查是否属于某个子树或其子节点
        List<String> subTrees = configService.getLdapTrees();
        if (!CollectionUtils.isEmpty(subTrees)) {
            for (String subTree : subTrees) {
                String lowerSubTree = subTree.toLowerCase();
                // 完全匹配或是子路径
                if (lowerDn.equals(lowerSubTree) || lowerDn.endsWith("," + lowerSubTree)) {
                    return true;
                }
            }
            // 如果配置了子树限制,但节点不属于任何子树,则排除
            return false;
        }

        // 如果没有配置子树限制,则包含 baseDn 下的所有节点
        return lowerDn.endsWith("," + lowerBase);
    }

    /**
     * 从 DN 中解析父 DN
     *
     * @param dn 当前 DN
     * @return 父 DN
     */
    private String getParentDn(String dn) {
        if (dn == null || dn.isEmpty()) {
            return null;
        }

        // DN 格式：cn=user,ou=dept,dc=example,dc=com
        int firstCommaIndex = dn.indexOf(',');
        if (firstCommaIndex == -1) {
            return null; // 没有父节点
        }

        String parentDn = dn.substring(firstCommaIndex + 1).trim();
        return parentDn.isEmpty() ? null : parentDn;
    }

    /**
     * 强制刷新 LDAP 树缓存（后台管理勾选部门时使用）
     */
    public void forceRefreshLdapCache() {
        deptNodeCache.clear();
        log.info("[LdapService] 部门节点缓存(deptNodeCache)已强制清空");
    }

    /**
     * 从 LDAP 查询平铺的节点列表(无树形关系)
     *
     * @return 平铺的节点列表
     */
    private List<LdapTreeNode> getLdapEntriesFromLdap() {
        String cacheKey = configService.getLdapBase();

        // 使用 AndFilter 和 HardcodedFilter 来查询所有条目，但排除组对象
        // 过滤条件：objectClass=person 或 objectClass=organizationalUnit，排除 group/groupOfNames
        AndFilter filter = new AndFilter();
        filter.and(new HardcodedFilter("(|(objectClass=person)(objectClass=organizationalUnit))"));

        // 查询该目录下的所有数据
        return ldapTemplate.search(
                LdapQueryBuilder.query()
                        .base(cacheKey)
                        .searchScope(SearchScope.SUBTREE)
                        .filter(filter.encode()),
                (ContextMapper<LdapTreeNode>) ctx -> {
                    DirContextAdapter context = (DirContextAdapter) ctx;
                    LdapTreeNode node = new LdapTreeNode();

                    // 获取 DN
                    String dn = context.getNameInNamespace();
                    node.setDn(dn);

                    // 获取所有属性
                    Attributes attrs = context.getAttributes();

                    // 获取名称 (优先 cn，其次 uid)
                    if (attrs.get("cn") != null) {
                        node.setName(attrs.get("cn").get().toString());
                    } else if (attrs.get("uid") != null) {
                        node.setName(attrs.get("uid").get().toString());
                    }

                    // 获取对象类
                    if (attrs.get("objectClass") != null) {
                        Attribute objClassAttr = attrs.get("objectClass");
                        try {
                            if (objClassAttr.size() > 0) {
                                node.setObjectClass(objClassAttr.get(0).toString());
                            }
                        } catch (NamingException e) {
                            log.warn("获取objectClass失败: {}", e.getMessage());
                        }
                    }

                    // 获取所有其他属性
                    var namingEnum = attrs.getAll();
                    while (namingEnum.hasMore()) {
                        try {
                            var attr = (Attribute) namingEnum.next();
                            String attrId = attr.getID();
                            // 跳过 dn、cn、uid、objectClass 这些已处理的属性
                            if (!attrId.equalsIgnoreCase("dn") &&
                                    !attrId.equalsIgnoreCase("cn") &&
                                    !attrId.equalsIgnoreCase("uid") &&
                                    !attrId.equalsIgnoreCase("objectClass")) {
                                if (attr.size() == 1) {
                                    node.getAttributes().put(attrId, attr.get().toString());
                                } else {
                                    List<String> values = new ArrayList<>();
                                    for (int i = 0; i < attr.size(); i++) {
                                        values.add(attr.get(i).toString());
                                    }
                                    node.getAttributes().put(attrId, values);
                                }
                            }
                        } catch (NamingException e) {
                            log.warn("获取属性失败: {}", e.getMessage());
                        }
                    }

                    return node;
                }
        );
    }

    /**
     * 暴露全量 LDAP 条目（person + organizationalUnit），供 LdapSyncService 同步使用
     */
    public List<LdapTreeNode> getAllLdapEntries() {
        return getLdapEntriesFromLdap();
    }

    /**
     * 缓存条目
     */
    @Data
    private static class CacheEntry {
        private List<LdapTreeNode> nodes;
        private long expireTime; // 过期时间戳(毫秒)

        public CacheEntry(List<LdapTreeNode> nodes, long ttlMillis) {
            this.nodes = nodes;
            this.expireTime = System.currentTimeMillis() + ttlMillis;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
    }

    /**
     * LDAP 树节点
     */
    @Data
    public static class LdapTreeNode {
        private String dn;  // 区分名
        private String name; // 名称 (cn 或 uid)
        private String objectClass; // 对象类
        private Map<String, Object> attributes; // 其他属性
        private List<LdapTreeNode> children; // 子节点

        public LdapTreeNode() {
            this.children = new ArrayList<>();
            this.attributes = new HashMap<>();
        }

        public String getDn() {
            return dn;
        }

        public void setDn(String dn) {
            this.dn = dn;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getObjectClass() {
            return objectClass;
        }

        public void setObjectClass(String objectClass) {
            this.objectClass = objectClass;
        }

        public Map<String, Object> getAttributes() {
            return attributes;
        }

        public void setAttributes(Map<String, Object> attributes) {
            this.attributes = attributes;
        }

        public List<LdapTreeNode> getChildren() {
            return children;
        }

        public void setChildren(List<LdapTreeNode> children) {
            this.children = children;
        }

        public void addChild(LdapTreeNode child) {
            this.children.add(child);
        }
    }
}
