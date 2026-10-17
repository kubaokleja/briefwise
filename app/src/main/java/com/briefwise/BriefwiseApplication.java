package com.briefwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BriefwiseApplication {

    public static void main(String[] args) {
        SpringApplication.run(BriefwiseApplication.class, args);
    }
}
