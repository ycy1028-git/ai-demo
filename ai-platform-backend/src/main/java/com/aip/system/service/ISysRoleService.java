package com.aip.system.service;

import com.aip.common.result.PageResult;
import com.aip.system.dto.MenuPermissionOptionVO;
import com.aip.system.dto.RoleDTO;
import com.aip.system.dto.RoleVO;
import com.aip.system.entity.SysRole;

import java.util.List;

public interface ISysRoleService {

    PageResult<RoleVO> page(String keyword, Integer status, int page, int size);

    List<RoleVO> list();

    RoleVO getById(String id);

    SysRole getEntityById(String id);

    RoleVO create(RoleDTO dto);

    RoleVO update(String id, RoleDTO dto);

    void delete(String id);

    void updateStatus(String id, Integer status);

    List<MenuPermissionOptionVO> getMenuOptions();
}
