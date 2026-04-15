package com.aip.system.service;

import com.aip.system.dto.LoginDTO;
import com.aip.system.dto.LoginVO;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * 认证服务接口
 */
public interface IAuthService {

    /**
     * 用户登录
     *
     * @param loginDTO 登录参数
     * @param request  HTTP请求
     * @return 登录响应
     */
    LoginVO login(LoginDTO loginDTO, HttpServletRequest request);

    /**
     * 用户退出
     *
     * @param token 用户令牌
     */
    void logout(String token);

    /**
     * 获取当前用户信息
     *
     * @param token 用户令牌
     * @return 当前用户
     */
    LoginVO getCurrentUser(String token);

    /**
     * 获取当前用户活动列表
     *
     * @param token 用户令牌
     * @return 活动列表
     */
    List<Map<String, Object>> getCurrentActivities(String token);
}
