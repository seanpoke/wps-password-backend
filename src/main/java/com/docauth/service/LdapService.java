package com.docauth.service;

import com.docauth.dto.LdapNodeDTO;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.ldap.core.AttributesMapper;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.query.LdapQueryBuilder;
import org.springframework.ldap.core.support.AbstractContextSource;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.query.SearchScope;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.HardcodedFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class LdapService {

    @Autowired
    private LdapTemplate ldapTemplate;

    @Value("${spring.ldap.base}")
    private String ldapBase;

    /**
     * 验证用户身份
     *
     * @param account  用户名
     * @param password 密码
     * @return 是否验证成功
     */
    public boolean authenticate(String account, String password) {
        try {
            // 构建 LDAP 查询，查找用户
            var query = LdapQueryBuilder.query()
                    .base(ldapBase)
                    .where("uid").is(account);

            // 尝试绑定用户，验证密码
            ldapTemplate.authenticate(query, password);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 查询用户的 LDAP 路径
     *
     * @param account 用户名
     * @return LDAP 路径
     */
    public String getUserPath(String account) {

        return null;
    }

    /**
     * 查询部门的 LDAP 路径
     *
     * @param deptName 部门名称
     * @return LDAP 路径
     */
    public String getDeptPath(String deptName) {

        return null;
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

    /**
     * 查询 LDAP 所有树形结构
     *
     * @return LDAP 树形结构列表
     */
    public List<LdapNodeDTO> getAllLdapTree() {
        // 使用 AndFilter 和 HardcodedFilter 来查询所有条目
        // 注意: EqualsFilter("objectClass", "*") 会将 * 当作字面量,无法匹配所有条目
        AndFilter filter = new AndFilter();
        filter.and(new HardcodedFilter("(objectClass=*)"));
        
        log.info("开始查询 LDAP 树形结构, base: {}", ldapBase);
        
        // 查询该目录下的所有数据
        List<LdapTreeNode> allEntries = ldapTemplate.search(
                LdapQueryBuilder.query()
                        .base(ldapBase)
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
                        node.setName(attrs.get("cn").toString());
                    } else if (attrs.get("uid") != null) {
                        node.setName(attrs.get("uid").toString());
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

        // 构建树形结构
        log.info("查询到 LDAP 节点数量: {}", allEntries.size());
        List<LdapTreeNode> tree = buildTree(allEntries);
        
        // 转换为精简的 DTO
        List<LdapNodeDTO> result = new ArrayList<>();
        for (LdapTreeNode node : tree) {
            result.add(toDTO(node));
        }
        return result;
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
     * 构建 LDAP 树形结构
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

            // 解析父 DN
            String parentDn = getParentDn(dn);

            if (parentDn == null || parentDn.isEmpty()) {
                // 根节点
                roots.add(node);
            } else {
                // 查找父节点并添加为子节点
                LdapTreeNode parentNode = dnMap.get(parentDn.toLowerCase());
                if (parentNode != null) {
                    parentNode.addChild(node);
                } else {
                    // 如果找不到父节点，也作为根节点
                    roots.add(node);
                }
            }
        }

        return roots;
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
}
