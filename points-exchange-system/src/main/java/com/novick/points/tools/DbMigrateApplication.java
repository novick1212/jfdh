package com.novick.points.tools;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.novick.points")
public class DbMigrateApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(DbMigrateApplication.class);
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("spring.main.web-application-type", "none");
        defaults.put("spring.profiles.active", "db-migrate");
        app.setDefaultProperties(defaults);
        app.run(args);
    }
}

