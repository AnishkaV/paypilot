package com.paypilot.payment;

import com.paypilot.common.runtime.JdkWorkarounds;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        JdkWorkarounds.apply();
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
