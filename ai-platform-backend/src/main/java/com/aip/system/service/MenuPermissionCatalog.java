package com.aip.system.service;

import com.aip.system.dto.MenuPermissionOptionVO;

import java.util.List;

public final class MenuPermissionCatalog {

    public static final String ALL = "*";

    private MenuPermissionCatalog() {
    }

    public static List<MenuPermissionOptionVO> options() {
        return List.of(
                new MenuPermissionOptionVO("dashboard", "工作台", "工作台"),

                new MenuPermissionOptionVO("knowledge.base", "知识库列表", "智能知识库"),
                new MenuPermissionOptionVO("knowledge.item", "知识列表", "智能知识库"),

                new MenuPermissionOptionVO("app.customer", "智能助手", "智能应用"),
                new MenuPermissionOptionVO("app.search", "智能搜索", "智能应用"),

                new MenuPermissionOptionVO("flow.template", "流程模板", "流程管理"),

                new MenuPermissionOptionVO("system.user", "用户管理", "系统管理"),
                new MenuPermissionOptionVO("system.role", "角色管理", "系统管理"),
                new MenuPermissionOptionVO("system.credential", "API凭证管理", "系统管理"),
                new MenuPermissionOptionVO("system.model", "大模型配置", "系统管理")
        );
    }
}
