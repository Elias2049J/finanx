package com.elias.mismonedas.shared.domain.vo;

/** Result of evaluating a business rule. */
public record Decision(boolean allowed, String reason) {

    public Decision {
        if (!allowed && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("A denied decision requires a reason");
        }
    }

    public static Decision accepted() {
        return new Decision(true, null);
    }

    public static Decision denied(String reason) {
        return new Decision(false, reason);
    }

    public Decision requireAllowed() {
        if (!allowed) {
            throw new IllegalStateException(reason);
        }
        return this;
    }
}
