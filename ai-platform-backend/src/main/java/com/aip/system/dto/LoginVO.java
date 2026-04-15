package com.aip.system.dto;

import lombok.Data;

import java.util.List;

/**
 * 登录响应DTO
 */
@Data
public class LoginVO {

    private String token;

    /** 用户ID（UUIDv7 无横杠字符串） */
    private String userId;

    private String username;

    private String realName;

    private String roleId;

    private String roleCode;

    private String roleName;

    private Boolean admin;

    private List<String> menuPermissions;

    private Long expireTime;
}
