package com.aip.system.controller;

import com.aip.common.result.Result;
import com.aip.system.dto.UserDTO;
import com.aip.system.entity.SysUser;
import com.aip.system.service.ISysUserService;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    @Autowired
    private ISysUserService sysUserService;

    /**
     * 分页查询用户
     */
    @GetMapping
    public Result<?> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(sysUserService.page(keyword, status, page, size));
    }

    /**
     * 查询所有用户
     */
    @GetMapping("/list")
    public Result<List<SysUser>> list() {
        return Result.ok(sysUserService.list());
    }

    /**
     * 根据ID查询
     */
    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable String id) {
        return Result.ok(sysUserService.getById(id));
    }

    /**
     * 创建用户
     */
    @PostMapping
    public Result<SysUser> create(@Valid @RequestBody UserDTO dto) {
        return Result.ok(sysUserService.create(dto));
    }

    /**
     * 更新用户
     */
    @PutMapping("/{id}")
    public Result<SysUser> update(@PathVariable String id, @Valid @RequestBody UserDTO dto) {
        return Result.ok(sysUserService.update(id, dto));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        sysUserService.delete(id);
        return Result.ok();
    }

    /**
     * 重置密码
     */
    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable String id, @RequestParam String password) {
        sysUserService.resetPassword(id, password);
        return Result.ok();
    }

    /**
     * 修改状态
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable String id, @RequestParam Integer status) {
        sysUserService.updateStatus(id, status);
        return Result.ok();
    }
}
