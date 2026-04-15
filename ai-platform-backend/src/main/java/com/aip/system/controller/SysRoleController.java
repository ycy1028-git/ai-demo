package com.aip.system.controller;

import com.aip.common.result.Result;
import com.aip.system.dto.MenuPermissionOptionVO;
import com.aip.system.dto.RoleDTO;
import com.aip.system.dto.RoleVO;
import com.aip.system.service.ISysRoleService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/system/role")
public class SysRoleController {

    @Autowired
    private ISysRoleService sysRoleService;

    @GetMapping
    public Result<?> page(@RequestParam(required = false) String keyword,
                          @RequestParam(required = false) Integer status,
                          @RequestParam(defaultValue = "1") int page,
                          @RequestParam(defaultValue = "10") int size) {
        return Result.ok(sysRoleService.page(keyword, status, page, size));
    }

    @GetMapping("/list")
    public Result<List<RoleVO>> list() {
        return Result.ok(sysRoleService.list());
    }

    @GetMapping("/{id}")
    public Result<RoleVO> getById(@PathVariable String id) {
        return Result.ok(sysRoleService.getById(id));
    }

    @PostMapping
    public Result<RoleVO> create(@Valid @RequestBody RoleDTO dto) {
        return Result.ok(sysRoleService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<RoleVO> update(@PathVariable String id, @Valid @RequestBody RoleDTO dto) {
        return Result.ok(sysRoleService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sysRoleService.delete(id);
        return Result.ok();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        sysRoleService.updateStatus(id, status);
        return Result.ok();
    }

    @GetMapping("/menu-options")
    public Result<List<MenuPermissionOptionVO>> menuOptions() {
        return Result.ok(sysRoleService.getMenuOptions());
    }
}
