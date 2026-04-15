package com.docauth.dto;

import lombok.Data;
import java.util.List;

@Data
public class DocUpdateRelRequest {
    private String docId;
    private String operate;
    private List<String> accountFullList;
    private List<String> deptFullList;
}