package com.helios.ekm.audit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class FixtureApplication {
    public static void main(String[] args) {
        System.setProperty("server.port", System.getenv().getOrDefault("PORT", "8081"));
        SpringApplication.run(FixtureApplication.class, args);
    }
}
