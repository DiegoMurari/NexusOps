package com.nexusops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulith;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@Modulith
// Sem isto nenhum @Scheduled roda (vigia de SLA, escalonamento de violações).
@EnableScheduling
public class NexusOpsApplication {
    public static void main(String[] args) {
        SpringApplication.run(NexusOpsApplication.class, args);
    }
}
