package com.briefwise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BriefwiseApplication {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(BriefwiseApplication.class, args);
        // Job runner (--briefwise.job=<id>): the job ran during startup; exit with its code (ADR 0016). Without this
        // the Kafka listener threads would keep the process alive.
        if (context.getEnvironment().containsProperty("briefwise.job")) {
            System.exit(SpringApplication.exit(context));
        }
    }
}
