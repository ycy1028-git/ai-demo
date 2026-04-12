# AI能力中台

企业级AI能力中台系统，基于Spring Boot + Vue3构建，提供知识库管理、智能问答、应用集成等核心功能。

## 功能特性

- **智能知识库**：支持多种文档格式（TXT、PDF、Word、HTML）的上传、解析和向量化存储
- **六大AI助手**：客服助手、搜索助手、HR助手、财务助手、培训助手、客资助手
- **RAG检索增强**：基于知识库的智能问答系统
- **API凭证管理**：安全的对外API访问密钥管理
- **用户权限管理**：完整的用户和角色管理

## 技术栈

### 后端
- Spring Boot 3.2 + Java 17
- Spring Data JPA (Hibernate自动建表)
- Spring Data Redis
- Spring Data Elasticsearch
- MinIO 对象存储
- Apache Tika 文档解析
- JWT 认证

### 前端
- Vue 3 + Vite 5
- Element Plus
- Pinia 状态管理
- Vue Router 4
- Axios

### 中间件
- MySQL 8.0
- Redis 7
- Elasticsearch 7.17
- MinIO

## 快速启动

### 1. 启动中间件（Docker）

```bash
cd docker
docker-compose up -d
```

### 2. 启动后端

```bash
cd ai-platform-backend

# 编译
mvn clean package -DskipTests

# 运行
java -jar target/ai-platform-1.0.0.jar
```

或使用IDE直接运行 `AiPlatformApplication.java`

### 3. 启动前端

```bash
cd ai-platform-frontend
npm install
npm run dev
```

### 4. 访问系统

- 前端页面：http://localhost:5173
- 后端API：http://localhost:8080
- 默认账号：admin / admin123

## 项目结构

```
ai-demo/
├── ai-platform-backend/     # 后端项目
│   ├── src/main/java/com/aip/
│   │   ├── common/          # 通用模块（配置、异常、结果、安全）
│   │   ├── system/          # 系统管理（用户、API凭证）
│   │   ├── knowledge/       # 知识库管理
│   │   └── app/             # 智能应用
│   └── src/main/resources/
│       └── application.yml  # 应用配置
│
├── ai-platform-frontend/   # 前端项目
│   ├── src/
│   │   ├── api/             # API接口
│   │   ├── views/           # 页面组件
│   │   ├── router/          # 路由配置
│   │   └── store/           # 状态管理
│   └── package.json
│
├── docker/                  # Docker配置
│   ├── docker-compose.yml   # 容器编排
│   ├── nginx.conf          # Nginx配置
│   └── .env                # 环境变量
│
└── scripts/                # 启动脚本
    ├── start-dev.sh        # 开发环境启动
    └── stop.sh             # 停止服务
```

## API接口

### 认证接口
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/logout` - 用户退出

### 系统管理
- `GET/POST/PUT/DELETE /api/system/user` - 用户管理
- `GET/POST/PUT/DELETE /api/system/api-credential` - API凭证管理

### 知识库
- `GET/POST/PUT/DELETE /api/kb/knowledge-base` - 知识库管理
- `GET/POST/PUT/DELETE /api/kb/knowledge-item` - 知识条目管理
- `POST /api/kb/document/upload` - 文档上传

### 智能应用
- `GET /api/app/assistant` - 助手列表
- `POST /api/app/chat` - 对话接口

## 配置说明

### 开发环境配置

编辑 `ai-platform-backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai_platform
    username: root
    password: root123
  
  data:
    redis:
      host: localhost
      port: 6379
  
  elasticsearch:
    uris: http://localhost:9200

minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
```

## 部署

### Docker Compose 一键部署

```bash
cd docker
docker-compose up -d
```

### 环境变量

| 变量名 | 说明 | 默认值 |
|--------|------|--------|
| MYSQL_ROOT_PASSWORD | MySQL root密码 | root123456 |
| MINIO_ACCESS_KEY | MinIO访问密钥 | minioadmin |
| MINIO_SECRET_KEY | MinIO密钥 | minioadmin123 |

## License

MIT
