package com.example.udriBook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class UdriBookApplication {

	public static void main(String[] args) {
		SpringApplication.run(UdriBookApplication.class, args);
	}

}
