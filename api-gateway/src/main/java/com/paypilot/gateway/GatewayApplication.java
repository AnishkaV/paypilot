package com.paypilot.gateway;

import com.paypilot.common.runtime.JdkWorkarounds;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        JdkWorkarounds.apply();
        SpringApplication.run(GatewayApplication.class, args);
    }
}
