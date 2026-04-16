package com.clinic.clinicmanager;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClinicManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClinicManagerApplication.class, args);
	}
}