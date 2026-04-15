package com.aip.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;

/**
 * 用户创建/更新DTO
 */
@Data
public class UserDTO {

    private String id;

    @NotBlank(message = "用户名不能为空")
    @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9_]{4,19}$", message = "用户名必须以字母开头，长度5-20位")
    private String username;

    @NotBlank(message = "真实姓名不能为空")
    @JsonAlias({"nickname"})
    private String realName;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String avatar;

    private Integer status = 1;

    private String roleId;

    /** 密码（创建时必填） */
    private String password;
}
