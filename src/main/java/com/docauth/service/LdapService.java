package com.docauth.service;

import com.docauth.context.UserContext;
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

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LdapService {

    @Autowired
    private LdapTemplate ldapTemplate;
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
            // 按当前"按 source 路由"模型：LDAP 查无此账号即认证失败，返回 null 交由上层
            // （AccountService）按来源分流处理，不再"回退本地认证"。
            log.debug("LDAP 未找到账号 {}，认证失败返回 null", account);
            return null;
        } catch (Exception e) {
            log.error("LDAP 认证异常: {}, 原因: {}", account, e.getMessage(), e);
            return null;
        }
    }



















    /**
     * 从 LDAP 按指定根（subTree 根或 baseDn）查询平铺的节点列表(无树形关系)
     *
     * @param base 搜索根 DN（一次 SUBTREE 搜索）
     * @return 平铺的节点列表
     */
    private List<LdapTreeNode> fetchFromLdap(String base) {
        // 使用 AndFilter 和 HardcodedFilter 来查询所有条目，但排除组对象
        // 过滤条件：objectClass=person 或 objectClass=organizationalUnit，排除 group/groupOfNames
        AndFilter filter = new AndFilter();
        filter.and(new HardcodedFilter("(|(objectClass=person)(objectClass=organizationalUnit))"));

        // 查询该根下的所有数据
        return ldapTemplate.search(
                LdapQueryBuilder.query()
                        .base(base)
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
     * 暴露全量 LDAP 条目（person + organizationalUnit），供 LdapSyncService 预览/apply 使用。
     * 按配置的 subTree 列表逐根拉取并合并去重；未配置 subTree 时返回空（不展示/不同步）。
     */
    public List<LdapTreeNode> getAllLdapEntries() {
        List<String> roots = configService.getLdapTrees();
        if (roots == null || roots.isEmpty()) {
            return List.of();
        }
        List<LdapTreeNode> all = new ArrayList<>();
        for (String root : roots) {
            all.addAll(fetchFromLdap(root));
        }
        return dedupe(all);
    }

    /**
     * 按 subTree 根分组拉取，供定时全量同步使用。
     * - 任一已配置根连接异常会向上抛出（由调用方判定跳过整轮）。
     * - 已配置根连接正常但返回 0 条时打 warning（可能 DN 写错或该分支已从 LDAP 删除）。
     */
    public Map<String, List<LdapTreeNode>> getAllLdapEntriesByRoot() {
        Map<String, List<LdapTreeNode>> byRoot = new HashMap<>();
        List<String> roots = configService.getLdapTrees();
        if (roots == null || roots.isEmpty()) {
            return byRoot;
        }
        for (String root : roots) {
            List<LdapTreeNode> entries = fetchFromLdap(root);
            byRoot.put(root, entries);
            if (entries.isEmpty()) {
                log.warn("[LDAP-SYNC] 已配置 subTree 根 {} 连接正常但返回 0 条节点（可能 DN 写错或该分支已从 LDAP 删除）", root);
            }
        }
        return byRoot;
    }

    private List<LdapTreeNode> dedupe(List<LdapTreeNode> list) {
        Map<String, LdapTreeNode> byDn = new LinkedHashMap<>();
        for (LdapTreeNode n : list) {
            if (n.getDn() != null) {
                byDn.putIfAbsent(n.getDn().toLowerCase(), n);
            }
        }
        return new ArrayList<>(byDn.values());
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
