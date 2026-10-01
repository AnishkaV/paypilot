package com.paypilot.mockpsp;

import com.paypilot.common.runtime.JdkWorkarounds;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MockPspApplication {

    public static void main(String[] args) {
        JdkWorkarounds.apply();
        SpringApplication.run(MockPspApplication.class, args);
    }
}
