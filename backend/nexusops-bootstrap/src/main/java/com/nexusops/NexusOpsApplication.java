package com.nexusops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;

@SpringBootApplication
@Modulith
public class NexusOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusOpsApplication.class, args);
    }
}
