package com.docauth.dto;

/**
 * Department reverse reference: which role/user directly set a department as its visible scope.
 * Used by the frontend "department authorization" reverse-lookup page.
 */
public class DeptVisibleRef {

    private Long id;
    private String relType;
    private Long relId;
    private String name;

    public DeptVisibleRef(Long id, String relType, Long relId, String name) {
        this.id = id;
        this.relType = relType;
        this.relId = relId;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getRelType() {
        return relType;
    }

    public Long getRelId() {
        return relId;
    }

    public String getName() {
        return name;
    }
}
