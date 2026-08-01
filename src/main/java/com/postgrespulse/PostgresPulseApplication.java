package com.postgrespulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PostgresPulseApplication {

    public static void main(String[] args) {
        SpringApplication.run(PostgresPulseApplication.class, args);
    }
}
