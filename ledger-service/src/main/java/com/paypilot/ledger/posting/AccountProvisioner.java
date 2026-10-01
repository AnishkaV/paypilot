package com.paypilot.ledger.posting;

import com.paypilot.ledger.domain.Account;
import com.paypilot.ledger.domain.AccountRepository;
import com.paypilot.ledger.domain.AccountType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates accounts on first use (a new merchant, a new PSP) in a separate, tiny transaction.
 *
 * <p>Why separate: two events for a brand new merchant can be posted at the same moment. One
 * insert wins, the other hits the unique index on accounts.code. On Postgres an error aborts the
 * whole transaction it happens in, so the losing insert must not run inside the posting
 * transaction. In its own REQUIRES_NEW transaction it can fail harmlessly, and the caller simply
 * reads the account the winner created.
 */
@Component
public class AccountProvisioner {

    private final AccountRepository accounts;

    public AccountProvisioner(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createIfMissing(String code, AccountType type, String currency) {
        if (accounts.findByCode(code).isPresent()) {
            return;
        }
        try {
            accounts.saveAndFlush(Account.of(code, type, currency));
        } catch (DataIntegrityViolationException raceLost) {
            // Another transaction created it first. That's fine: the account exists now.
        }
    }
}
