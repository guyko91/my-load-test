package com.dw.idstrust.loadtesttoy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LoadTestToyApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoadTestToyApplication.class, args);
    }

}
