package com.paypilot.ledger.domain;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String code;
    @Enumerated(EnumType.STRING)
    private AccountType type;
    private String currency;
    private Instant createdAt;

    protected Account() {
        // for JPA
    }

    public static Account of(String code, AccountType type, String currency) {
        Account account = new Account();
        account.code = code;
        account.type = type;
        account.currency = currency;
        account.createdAt = Instant.now();
        return account;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
