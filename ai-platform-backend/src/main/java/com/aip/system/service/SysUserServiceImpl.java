package com.aip.system.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.common.utils.PasswordEncoder;
import com.aip.system.dto.UserDTO;
import com.aip.system.entity.SysRole;
import com.aip.system.entity.SysUser;
import com.aip.system.mapper.SysRoleMapper;
import com.aip.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Override
    public PageResult<SysUser> page(String keyword, Integer status, int page, int size) {
        int safePage = page <= 0 ? 1 : page;
        int safeSize = size <= 0 ? 10 : size;
        String normalizedKeyword = keyword == null ? null : keyword.trim();

        List<SysUser> filtered = sysUserMapper.findAllActive();
        if (normalizedKeyword != null && !normalizedKeyword.isBlank()) {
            filtered = filtered.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().contains(normalizedKeyword))
                            || (u.getRealName() != null && u.getRealName().contains(normalizedKeyword)))
                    .collect(Collectors.toList());
        }
        if (status != null) {
            filtered = filtered.stream()
                    .filter(u -> Objects.equals(u.getStatus(), status))
                    .collect(Collectors.toList());
        }
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<SysUser> records = fromIndex >= filtered.size()
                ? Collections.<SysUser>emptyList()
                : filtered.subList(fromIndex, toIndex);
        return PageResult.of(Long.valueOf(filtered.size()), records, Long.valueOf(safePage), Long.valueOf(safeSize));
    }

    @Override
    public List<SysUser> list() {
        return sysUserMapper.findAllActive();
    }

    @Override
    public SysUser getById(String id) {
        return sysUserMapper.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public SysUser create(UserDTO dto) {
        if (sysUserMapper.existsByUsername(dto.getUsername())) {
            throw new BusinessException("用户名已存在");
        }
        if (dto.getEmail() != null && sysUserMapper.existsByEmail(dto.getEmail())) {
            throw new BusinessException("邮箱已被使用");
        }
        if (dto.getPhone() != null && sysUserMapper.existsByPhone(dto.getPhone())) {
            throw new BusinessException("手机号已被使用");
        }
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new BusinessException("密码不能为空");
        }

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(PasswordEncoder.encode(dto.getPassword()));
        user.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        user.setRoleId(resolveRoleId(dto.getRoleId()));
        return sysUserMapper.save(user);
    }

    @Override
    @Transactional
    public SysUser update(String id, UserDTO dto) {
        SysUser user = sysUserMapper.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        String username = dto.getUsername() == null || dto.getUsername().isBlank() ? user.getUsername() : dto.getUsername();
        if (!user.getUsername().equals(username) && sysUserMapper.existsByUsername(username)) {
            throw new BusinessException("用户名已存在");
        }

        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setAvatar(dto.getAvatar());
        user.setStatus(dto.getStatus() == null ? user.getStatus() : dto.getStatus());
        user.setRoleId(resolveRoleId(dto.getRoleId()));

        return sysUserMapper.save(user);
    }

    @Override
    @Transactional
    public void delete(String id) {
        SysUser user = sysUserMapper.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException("不能删除超级管理员");
        }
        sysUserMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void resetPassword(String id, String newPassword) {
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6) {
            throw new BusinessException("新密码至少6位");
        }
        SysUser user = sysUserMapper.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        user.setPassword(PasswordEncoder.encode(newPassword));
        sysUserMapper.save(user);
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        SysUser user = sysUserMapper.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));
        if ("admin".equals(user.getUsername())) {
            throw new BusinessException("不能禁用超级管理员");
        }
        user.setStatus(status);
        sysUserMapper.save(user);
    }

    private String resolveRoleId(String roleId) {
        if (roleId != null && !roleId.isBlank()) {
            SysRole role = sysRoleMapper.findById(roleId)
                    .orElseThrow(() -> new BusinessException("角色不存在"));
            if (role.getDeleted() != null && role.getDeleted()) {
                throw new BusinessException("角色不存在");
            }
            if (role.getStatus() != null && role.getStatus() == 0) {
                throw new BusinessException("角色已禁用，不能分配");
            }
            return roleId;
        }
        return sysRoleMapper.findByCode("USER").map(SysRole::getId).orElse(null);
    }
}
