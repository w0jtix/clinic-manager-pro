package com.clinic.clinicmanager;

import com.clinic.clinicmanager.support.AbstractPostgresTest;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ClinicManagerApplicationTests extends AbstractPostgresTest {

	@Test
	void contextLoads() {
	}

}
