package com.mobai.alert;

import com.mobai.alert.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Spring Boot 上下文加载测试。
 */
@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "market.data.mode=rest",
        "monitoring.proxy.enabled=false",
        "notification.feishu.webhook-url=http://localhost/test"
})
class AlertApplicationTests {

    @MockitoBean
    private AlertService alertService;

    /**
     * 验证应用上下文可以正常启动。
     */
    @Test
    void contextLoads() {
    }
}
