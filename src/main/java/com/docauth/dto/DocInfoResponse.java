package com.docauth.dto;

import lombok.Data;

@Data
public class DocInfoResponse {
    private String publicKey;
    private String owner;
    private String uid;
}