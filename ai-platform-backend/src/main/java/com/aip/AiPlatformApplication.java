package com.aip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * AI能力中台系统启动类
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableAsync
@EnableScheduling
public class AiPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiPlatformApplication.class, args);
        System.out.println("""
                
                ╔═══════════════════════════════════════════════════════════════╗
                ║                                                               ║
                ║              AI能力中台系统 启动成功                           ║
                ║                                                               ║
                ║    前端访问地址: http://localhost:3000                        ║
                ║    后端接口地址: http://localhost:8080                        ║
                ║                                                               ║
                ║    默认管理员账号: admin                                       ║
                ║    默认管理员密码: admin123                                   ║
                ║                                                               ║
                ╚═══════════════════════════════════════════════════════════════╝
                """);
    }
}
