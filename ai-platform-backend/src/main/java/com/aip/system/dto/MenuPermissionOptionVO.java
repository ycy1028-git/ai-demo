package com.aip.system.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class MenuPermissionOptionVO {
    private String code;
    private String label;
    private String group;
}
