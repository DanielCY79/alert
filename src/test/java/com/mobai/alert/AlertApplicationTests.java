package com.mobai.alert;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mobai.alert.service.AlertService;

@SpringBootTest(properties = {
        "spring.task.scheduling.enabled=false",
        "market.data.mode=rest",
        "monitoring.proxy.enabled=false",
        "notification.feishu.webhook-url=http://localhost/test"
})
class AlertApplicationTests {

	@MockitoBean
	private AlertService alertService;

	@Test
	void contextLoads() {
	}

}
