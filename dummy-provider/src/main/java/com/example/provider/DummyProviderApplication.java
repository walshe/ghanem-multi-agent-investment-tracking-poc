package com.example.provider;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Responsibility: start the Spring Boot application.
 *
 * <p>Nothing else lives here. Configuration properties are picked up by scanning, and every
 * other concern has its own class.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DummyProviderApplication {

    public static void main(String[] args) {
        SpringApplication.run(DummyProviderApplication.class, args);
    }
}
