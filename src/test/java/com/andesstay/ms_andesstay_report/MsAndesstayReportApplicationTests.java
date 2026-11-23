package com.andesstay.ms_andesstay_report;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// Sin brokers en los tests: no arrancan los listeners ni se crean tópicos
@SpringBootTest(properties = {"spring.kafka.listener.auto-startup=false", "spring.kafka.admin.auto-create=false"})
class MsAndesstayReportApplicationTests {

	@Test
	void contextLoads() {
	}

}
