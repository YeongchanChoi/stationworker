package com.example.subway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // 스케줄링 기능
public class SubwayApplication {
	public static void main(String[] args) {
		SpringApplication.run(SubwayApplication.class, args);
	}
}
