package com.example.udriBook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;

@SpringBootApplication
@EnableScheduling
public class UdriBookApplication {

	public static void main(String[] args) {
		new org.springframework.boot.builder.SpringApplicationBuilder(UdriBookApplication.class)
			// Flyway owns the schema. Hibernate must NEVER validate or update.
			// This overrides any env var (e.g. DDL_AUTO=validate on Railway).
			.properties("spring.jpa.hibernate.ddl-auto=none")
			.run(args);
	}

	@Bean
	public FlywayMigrationStrategy cleanMigrateStrategy() {
		return flyway -> {
			flyway.repair();
			flyway.migrate();
		};
	}

}
