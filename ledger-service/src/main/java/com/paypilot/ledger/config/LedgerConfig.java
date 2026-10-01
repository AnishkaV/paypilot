package com.paypilot.ledger.config;

import com.paypilot.ledger.posting.PostingRules;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LedgerConfig {

    @Bean
    PostingRules postingRules(LedgerProperties properties) {
        return new PostingRules(properties.feeBps());
    }
}
