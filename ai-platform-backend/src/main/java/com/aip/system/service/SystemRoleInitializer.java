package com.aip.system.service;

import com.aip.system.entity.SysRole;
import com.aip.system.entity.SysUser;
import com.aip.system.mapper.SysRoleMapper;
import com.aip.system.mapper.SysUserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
public class SystemRoleInitializer implements ApplicationRunner {

    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private SysRoleServiceImpl roleService;

    @Override
    public void run(ApplicationArguments args) {
        SysRole adminRole = ensureRole("ADMIN", "系统管理员", "系统管理员，默认拥有全部菜单权限", List.of(MenuPermissionCatalog.ALL), true);
        ensureRole("MANAGER", "运营管理员", "业务管理角色", List.of(
                "dashboard", "knowledge.base", "knowledge.item", "app.customer", "app.search", "flow.template", "system.credential", "system.model"
        ), true);
        ensureRole("USER", "普通用户", "普通使用角色", List.of("dashboard", "app.customer", "app.search"), true);

        sysUserMapper.findByUsername("admin").ifPresent(admin -> bindAdminRole(admin, adminRole));
    }

    private SysRole ensureRole(String code, String name, String description, List<String> permissions, boolean builtIn) {
        return sysRoleMapper.findByCode(code).map(existing -> {
            if (existing.getMenuPermissions() == null || existing.getMenuPermissions().isBlank()) {
                existing.setMenuPermissions(roleService.serializePermissions(permissions));
            }
            existing.setName(name);
            existing.setDescription(description);
            existing.setBuiltIn(true);
            if (existing.getStatus() == null) {
                existing.setStatus(1);
            }
            return sysRoleMapper.save(existing);
        }).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setStatus(1);
            role.setBuiltIn(builtIn);
            role.setMenuPermissions(roleService.serializePermissions(permissions));
            return sysRoleMapper.save(role);
        });
    }

    private void bindAdminRole(SysUser admin, SysRole adminRole) {
        if (adminRole == null || admin == null) {
            return;
        }
        if (adminRole.getId().equals(admin.getRoleId())) {
            return;
        }
        admin.setRoleId(adminRole.getId());
        sysUserMapper.save(admin);
        log.info("已为 admin 用户绑定系统管理员角色");
    }
}
