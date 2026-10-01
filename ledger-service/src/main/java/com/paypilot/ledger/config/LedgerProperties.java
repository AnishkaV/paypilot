package com.paypilot.ledger.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param feeBps platform fee in basis points (1 bp = 0.01%). 200 = 2%.
 */
@ConfigurationProperties("paypilot.ledger")
public record LedgerProperties(int feeBps) {
}
