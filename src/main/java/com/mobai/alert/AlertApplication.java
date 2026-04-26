package com.mobai.alert;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 告警服务启动入口，负责加载 Spring Boot 容器并启用定时任务。
 */
@SpringBootApplication
@EnableScheduling
public class AlertApplication {

    /**
     * 启动应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AlertApplication.class, args);
    }
}
