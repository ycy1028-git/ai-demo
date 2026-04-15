package com.aip.system.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoleVO {

    private String id;
    private String code;
    private String name;
    private String description;
    private Integer status;
    private Boolean builtIn;
    private List<String> menuPermissions;
    private String createTime;
    private String updateTime;
}
