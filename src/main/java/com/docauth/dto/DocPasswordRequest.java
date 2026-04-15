package com.docauth.dto;

import lombok.Data;

@Data
public class DocPasswordRequest {
    private String docId;
    private String account;
    private String encryPassword;
}