# AI能力中台 - 前端项目

基于 Vue3 + Vite + Element Plus 的 AI 能力中台前端应用。

## 功能特性

- **工作台**：统计概览、快捷入口、近期活动
- **智能知识库**：知识库管理、知识条目管理
- **智能应用**：6个 AI 助手（客服、搜索、HR、财务、培训、客资）
- **系统管理**：用户管理、API 凭证管理

## 技术栈

- Vue 3
- Vite 5
- Vue Router 4
- Pinia
- Element Plus
- Axios
- @vueuse/core

## 快速开始

```bash
# 安装依赖
npm install

# 启动开发服务器
npm run dev

# 构建生产版本
npm run build
```

## 项目结构

```
src/
├── api/           # API 接口
├── assets/        # 静态资源
├── components/    # 通用组件
├── composables/  # 组合式函数
├── router/        # 路由配置
├── store/         # 状态管理
├── utils/         # 工具函数
├── views/         # 页面视图
├── styles/        # 全局样式
├── App.vue        # 根组件
└── main.js        # 入口文件
```

## 菜单结构

1. **工作台** - 仪表盘
2. **智能知识库** - 知识库列表、知识列表
3. **智能应用** - 6个助手页面
4. **系统管理** - 用户管理、API凭证管理

## 默认账号

- 用户名：admin
- 密码：admin123
