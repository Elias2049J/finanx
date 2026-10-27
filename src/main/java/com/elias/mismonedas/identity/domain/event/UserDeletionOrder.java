package com.elias.mismonedas.identity.domain.event;

/**
 * Synchronous cleanup order for UserDeletedEvent handlers.
 * Child records must be removed before the rows they reference.
 */
public final class UserDeletionOrder {

    public static final int EMAIL_VERIFICATION = 4;
    public static final int USER_CONTEXT = 5;
    public static final int BILLING = 6;
    public static final int CHAT = 7;
    public static final int NOTIFICATIONS = 10;
    public static final int RECEIPTS = 15;
    public static final int LEDGER = 20;
    public static final int IMAGES = 25;
    public static final int PLANNING = 30;
    public static final int SCHEDULING = 40;
    public static final int CATALOG = 50;
    public static final int WALLETS = 60;

    private UserDeletionOrder() {
    }
}
