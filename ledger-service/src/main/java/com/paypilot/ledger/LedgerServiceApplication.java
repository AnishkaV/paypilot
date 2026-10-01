package com.paypilot.ledger;

import com.paypilot.common.runtime.JdkWorkarounds;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LedgerServiceApplication {

    public static void main(String[] args) {
        JdkWorkarounds.apply();
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
