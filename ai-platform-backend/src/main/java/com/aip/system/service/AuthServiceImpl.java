package com.aip.system.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.security.JwtUtil;
import com.aip.common.utils.PasswordEncoder;
import com.aip.system.dto.LoginDTO;
import com.aip.system.dto.LoginVO;
import com.aip.system.entity.SysUser;
import com.aip.system.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 认证服务实现
 */
@Slf4j
@Service
public class AuthServiceImpl implements IAuthService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    @Transactional
    public LoginVO login(LoginDTO loginDTO, HttpServletRequest request) {
        SysUser user = sysUserMapper.findActiveByUsername(loginDTO.getUsername())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));

        if (user.getStatus() == 0) {
            throw new BusinessException(401, "账号已被禁用");
        }

        if (!PasswordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(401, "用户名或密码错误");
        }

        String token = jwtUtil.createToken(user.getId(), user.getUsername());

        // 使用原生 UPDATE 更新登录信息，避免乐观锁冲突
        sysUserMapper.updateLoginInfo(user.getId(), Instant.now(), getClientIp(request));

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());

        return vo;
    }

    @Override
    public void logout(String token) {
        jwtUtil.removeToken(token);
    }

    @Override
    public SysUser getCurrentUser(String token) {
        String userId = jwtUtil.getUserId(token);
        return sysUserMapper.findById(userId).orElse(null);
    }

    @Override
    public List<Map<String, Object>> getCurrentActivities(String token) {
        // 返回模拟的活动列表，实际可关联操作日志表
        List<Map<String, Object>> activities = new ArrayList<>();

        activities.add(Map.of(
                "id", 1,
                "type", "login",
                "content", "用户登录系统",
                "time", LocalDateTime.now().toString()
        ));

        return activities;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
