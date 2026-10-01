package com.paypilot.ledger.posting;

/** Chart of accounts, from PayPilot's point of view as the platform. */
public final class AccountCodes {

    /** Fee income PayPilot earns on every successful payment. */
    public static final String PLATFORM_FEE_REVENUE = "platform_fee_revenue";

    private AccountCodes() {
    }

    /** Asset: money a PSP has collected from customers and still owes us. */
    public static String pspReceivable(String psp) {
        return "psp_receivable:" + psp;
    }

    /** Liability: money we owe the merchant, paid out at settlement. */
    public static String merchantPayable(String merchantId) {
        return "merchant_payable:" + merchantId;
    }
}
