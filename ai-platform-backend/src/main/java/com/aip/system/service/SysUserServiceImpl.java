package com.aip.system.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.common.utils.PasswordEncoder;
import com.aip.system.dto.UserDTO;
import com.aip.system.entity.SysUser;
import com.aip.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户服务实现
 */
@Slf4j
@Service
public class SysUserServiceImpl implements ISysUserService {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public PageResult<SysUser> page(String keyword, Integer status, int page, int size) {
        Page<SysUser> all = sysUserMapper.findAll(PageRequest.of(page - 1, size));
        List<SysUser> filtered = all.getContent();
        if (keyword != null && !keyword.isBlank()) {
            filtered = filtered.stream()
                    .filter(u -> u.getUsername().contains(keyword) || u.getRealName().contains(keyword))
                    .toList();
        }
        if (status != null) {
            filtered = filtered.stream().filter(u -> u.getStatus().equals(status)).toList();
        }
        return PageResult.of(all.getTotalElements(), filtered, (long) page, (long) size);
    }

    @Override
    public List<SysUser> list() {
        return sysUserMapper.findAll();
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

        SysUser user = new SysUser();
        BeanUtils.copyProperties(dto, user);
        user.setPassword(PasswordEncoder.encode(dto.getPassword()));
        user.setStatus(1);
        return sysUserMapper.save(user);
    }

    @Override
    @Transactional
    public SysUser update(String id, UserDTO dto) {
        SysUser user = sysUserMapper.findById(id)
                .orElseThrow(() -> new BusinessException("用户不存在"));

        if (!user.getUsername().equals(dto.getUsername()) && sysUserMapper.existsByUsername(dto.getUsername())) {
            throw new BusinessException("用户名已存在");
        }

        user.setRealName(dto.getRealName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setAvatar(dto.getAvatar());
        user.setStatus(dto.getStatus());

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
}
