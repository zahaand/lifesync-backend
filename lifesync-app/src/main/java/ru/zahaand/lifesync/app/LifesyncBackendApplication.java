package ru.zahaand.lifesync.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "ru.zahaand.lifesync")
@EnableScheduling
public class LifesyncBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifesyncBackendApplication.class, args);
    }

}
