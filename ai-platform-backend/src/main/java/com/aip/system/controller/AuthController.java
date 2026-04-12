package com.aip.system.controller;

import com.aip.common.result.Result;
import com.aip.system.dto.LoginDTO;
import com.aip.system.dto.LoginVO;
import com.aip.system.service.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 认证控制器
 */
@Slf4j
@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private IAuthService authService;

    /**
     * 用户登录
     */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO loginDTO, HttpServletRequest request) {
        return Result.ok(authService.login(loginDTO, request));
    }

    /**
     * 用户退出
     */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        authService.logout(token);
        return Result.ok();
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/current")
    public Result<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        return Result.ok(authService.getCurrentUser(token));
    }

    /**
     * 获取当前用户活动列表
     */
    @GetMapping("/current/activities")
    public Result<List<Map<String, Object>>> getCurrentActivities(@RequestHeader("Authorization") String token) {
        return Result.ok(authService.getCurrentActivities(token));
    }
}
