package ru.zahaand.lifesync.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ru.zahaand.lifesync")
public class LifesyncBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LifesyncBackendApplication.class, args);
    }

}
