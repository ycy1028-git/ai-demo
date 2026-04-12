package com.aip.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 文档配置
 *
 * <p>配置 Swagger UI 和 OpenAPI 3.0 规范
 *
 * <p>访问地址：
 * <ul>
 *   <li>Swagger UI: http://localhost:8080/swagger-ui.html
 *   <li>OpenAPI JSON: http://localhost:8080/v3/api-docs
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Value("${spring.application.name:ai-platform}")
    private String applicationName;

    /**
     * OpenAPI Bean 配置
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title(applicationName + " API")
                        .description("AI能力中台系统 API 文档\n\n" +
                                "## 模块说明\n\n" +
                                "- **系统模块**：用户管理、API凭证、操作日志\n" +
                                "- **应用模块**：AI助手、对话会话、调用统计\n" +
                                "- **知识库模块**：知识库管理、知识条目、文档处理\n" +
                                "- **流程模块**：工作流管理\n\n" +
                                "## 认证说明\n\n" +
                                "API 使用 JWT Token 进行认证，请在请求头中添加：\n" +
                                "```\nAuthorization: Bearer <your-token>\n```")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@aip.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("本地开发环境"),
                        new Server().url("${aip.server.url:http://localhost:8080}").description("生产环境")
                ))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("输入 JWT Token")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
