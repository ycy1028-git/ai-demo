package com.aip.system.service;

import com.aip.common.exception.BusinessException;
import com.aip.common.result.PageResult;
import com.aip.system.dto.MenuPermissionOptionVO;
import com.aip.system.dto.RoleDTO;
import com.aip.system.dto.RoleVO;
import com.aip.system.entity.SysRole;
import com.aip.system.mapper.SysRoleMapper;
import com.aip.system.mapper.SysUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysRoleServiceImpl implements ISysRoleService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public PageResult<RoleVO> page(String keyword, Integer status, int page, int size) {
        int safePage = page <= 0 ? 1 : page;
        int safeSize = size <= 0 ? 10 : size;

        List<RoleVO> filtered = sysRoleMapper.findAllActive().stream()
                .filter(role -> role.getDeleted() == null || !role.getDeleted())
                .filter(role -> keyword == null || keyword.isBlank() || role.getName().contains(keyword) || role.getCode().contains(keyword))
                .filter(role -> status == null || status.equals(role.getStatus()))
                .map(this::toVO)
                .collect(Collectors.toList());
        int fromIndex = Math.max((safePage - 1) * safeSize, 0);
        int toIndex = Math.min(fromIndex + safeSize, filtered.size());
        List<RoleVO> records = fromIndex >= filtered.size()
                ? Collections.<RoleVO>emptyList()
                : filtered.subList(fromIndex, toIndex);
        return PageResult.of(Long.valueOf(filtered.size()), records, Long.valueOf(safePage), Long.valueOf(safeSize));
    }

    @Override
    public List<RoleVO> list() {
        return sysRoleMapper.findAllActive().stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public RoleVO getById(String id) {
        return toVO(getEntityById(id));
    }

    @Override
    public SysRole getEntityById(String id) {
        return sysRoleMapper.findById(id)
                .filter(role -> role.getDeleted() == null || !role.getDeleted())
                .orElseThrow(() -> new BusinessException("角色不存在"));
    }

    @Override
    @Transactional
    public RoleVO create(RoleDTO dto) {
        String code = normalizeCode(dto.getCode());
        if (sysRoleMapper.existsByCode(code)) {
            throw new BusinessException("角色编码已存在");
        }
        if (sysRoleMapper.existsByName(dto.getName().trim())) {
            throw new BusinessException("角色名称已存在");
        }

        SysRole role = new SysRole();
        role.setCode(code);
        role.setName(dto.getName().trim());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() == null ? 1 : dto.getStatus());
        role.setBuiltIn(false);
        role.setMenuPermissions(serializePermissions(dto.getMenuPermissions()));

        return toVO(sysRoleMapper.save(role));
    }

    @Override
    @Transactional
    public RoleVO update(String id, RoleDTO dto) {
        SysRole role = getEntityById(id);
        String code = normalizeCode(dto.getCode());

        if (sysRoleMapper.existsByCodeAndIdNot(code, id)) {
            throw new BusinessException("角色编码已存在");
        }
        if (sysRoleMapper.existsByNameAndIdNot(dto.getName().trim(), id)) {
            throw new BusinessException("角色名称已存在");
        }
        if (Boolean.TRUE.equals(role.getBuiltIn()) && !role.getCode().equals(code)) {
            throw new BusinessException("内置角色不允许修改编码");
        }

        role.setCode(code);
        role.setName(dto.getName().trim());
        role.setDescription(dto.getDescription());
        role.setStatus(dto.getStatus() == null ? role.getStatus() : dto.getStatus());
        role.setMenuPermissions(serializePermissions(dto.getMenuPermissions()));

        return toVO(sysRoleMapper.save(role));
    }

    @Override
    @Transactional
    public void delete(String id) {
        SysRole role = getEntityById(id);
        if (Boolean.TRUE.equals(role.getBuiltIn())) {
            throw new BusinessException("内置角色不允许删除");
        }
        long bindCount = sysUserMapper.countByRoleId(id);
        if (bindCount > 0) {
            throw new BusinessException("该角色已授予用户，不能删除");
        }
        sysRoleMapper.deleteById(id);
    }

    @Override
    @Transactional
    public void updateStatus(String id, Integer status) {
        SysRole role = getEntityById(id);
        if (Boolean.TRUE.equals(role.getBuiltIn()) && status != null && status == 0) {
            throw new BusinessException("内置角色不能禁用");
        }
        role.setStatus(status);
        sysRoleMapper.save(role);
    }

    @Override
    public List<MenuPermissionOptionVO> getMenuOptions() {
        return MenuPermissionCatalog.options();
    }

    private RoleVO toVO(SysRole role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId());
        vo.setCode(role.getCode());
        vo.setName(role.getName());
        vo.setDescription(role.getDescription());
        vo.setStatus(role.getStatus());
        vo.setBuiltIn(role.getBuiltIn());
        vo.setMenuPermissions(parsePermissions(role.getMenuPermissions()));
        vo.setCreateTime(role.getCreateTime() == null ? null : FORMATTER.format(role.getCreateTime()));
        vo.setUpdateTime(role.getUpdateTime() == null ? null : FORMATTER.format(role.getUpdateTime()));
        return vo;
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessException("角色编码不能为空");
        }
        return code.trim().toUpperCase();
    }

    public List<String> parsePermissions(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            log.warn("角色菜单权限解析失败: {}", json, e);
            return Collections.emptyList();
        }
    }

    public String serializePermissions(List<String> permissions) {
        try {
            Set<String> normalized = new LinkedHashSet<>();
            if (permissions != null) {
                for (String permission : permissions) {
                    if (permission != null && !permission.isBlank()) {
                        normalized.add(permission.trim());
                    }
                }
            }
            if (normalized.isEmpty()) {
                normalized.add("dashboard");
            }
            return objectMapper.writeValueAsString(normalized);
        } catch (Exception e) {
            throw new BusinessException("菜单权限保存失败");
        }
    }
}
